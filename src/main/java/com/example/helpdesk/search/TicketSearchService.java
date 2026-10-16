package com.example.helpdesk.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.common.ServiceUnavailableException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.Comment;
import com.example.helpdesk.ticket.CommentRepository;
import com.example.helpdesk.ticket.Ticket;
import com.example.helpdesk.ticket.TicketRepository;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.user.Role;

@Service
@Transactional(readOnly = true)
public class TicketSearchService {

	// Wrapped around each matched word in highlight snippets. Characters from Unicode's
	// "private use" area: they never appear in normal text, so the UI can split on them safely.
	public static final String MARK_START = "";

	public static final String MARK_END = "";

	private static final Logger log = LoggerFactory.getLogger(TicketSearchService.class);

	private static final int MAX_RESULTS = 20;

	private final ElasticsearchOperations elasticsearch;

	private final TicketRepository tickets;

	private final CommentRepository comments;

	private volatile boolean indexReady;

	public TicketSearchService(ElasticsearchOperations elasticsearch, TicketRepository tickets,
			CommentRepository comments) {
		this.elasticsearch = elasticsearch;
		this.tickets = tickets;
		this.comments = comments;
	}

	// Creates the index with our mappings (analyzers, field types) if it doesn't exist yet.
	// Must happen before the first document is saved, or Elasticsearch would guess the
	// mappings itself and text wouldn't be analyzed as English.
	public void ensureIndex() {
		if (indexReady) {
			return;
		}
		IndexOperations index = elasticsearch.indexOps(TicketDocument.class);
		if (!index.exists()) {
			index.createWithMapping();
			log.info("Created Elasticsearch index 'tickets'");
		}
		indexReady = true;
	}

	// Copies one ticket's current state from Postgres into the index (or removes it if the
	// ticket no longer exists). Saving a document with the same id replaces it, so running
	// this twice is harmless: indexing is naturally idempotent.
	public void index(Long ticketId) {
		ensureIndex();
		tickets.findById(ticketId)
			.ifPresentOrElse(ticket -> elasticsearch.save(toDocument(ticket)),
					() -> elasticsearch.delete(String.valueOf(ticketId), TicketDocument.class));
	}

	// Rebuilds the index from Postgres, 100 tickets at a time. Needed for tickets created
	// before search existed, or after the index was lost or its mappings changed.
	public int reindexAll() {
		ensureIndex();
		int count = 0;
		Page<Ticket> page;
		int pageNumber = 0;
		do {
			page = tickets.findAll(PageRequest.of(pageNumber++, 100));
			List<TicketDocument> documents = page.map(this::toDocument).getContent();
			if (!documents.isEmpty()) {
				elasticsearch.save(documents);
			}
			count += documents.size();
		}
		while (page.hasNext());
		return count;
	}

	public List<TicketSearchResult> search(String text, HelpdeskUserDetails me) {
		boolean customer = me.getRole() == Role.CUSTOMER;
		SearchHits<TicketDocument> hits;
		try {
			ensureIndex();
			hits = elasticsearch.search(buildQuery(text, customer ? me.getId() : null), TicketDocument.class);
		}
		catch (RuntimeException ex) {
			throw new ServiceUnavailableException("Search is temporarily unavailable", ex);
		}

		// The index only finds the ids; the ticket data shown comes from Postgres, the source
		// of truth (the index can lag behind by a moment). Order follows the search ranking.
		List<Long> ids = hits.getSearchHits().stream().map(hit -> hit.getContent().id()).toList();
		Map<Long, Ticket> byId = tickets.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(Ticket::getId, Function.identity()));

		List<TicketSearchResult> results = new ArrayList<>();
		for (SearchHit<TicketDocument> hit : hits.getSearchHits()) {
			Ticket ticket = byId.get(hit.getContent().id());
			// Skip tickets deleted since indexing, and (defense in depth, on top of the query
			// filter) anything a customer doesn't own.
			if (ticket == null || (customer && !ticket.getReporter().getId().equals(me.getId()))) {
				continue;
			}
			results.add(new TicketSearchResult(TicketResponse.from(ticket), highlights(hit)));
		}
		return results;
	}

	// The query, in Elasticsearch's query language:
	// - multi_match: look for the words in title, description and comments; a match in the
	//   title counts three times as much (^3). fuzziness AUTO tolerates typos ("printr").
	// - filter: for customers, only their own tickets. A filter only includes or excludes;
	//   unlike "must", it doesn't affect the relevance score.
	private NativeQuery buildQuery(String text, Long reporterId) {
		HighlightParameters highlightParameters = HighlightParameters.builder()
			.withPreTags(MARK_START)
			.withPostTags(MARK_END)
			.withFragmentSize(150)
			.withNumberOfFragments(2)
			.build();
		Highlight highlight = new Highlight(highlightParameters,
				List.of(new HighlightField("title"), new HighlightField("description"), new HighlightField("comments")));

		return NativeQuery.builder().withQuery(q -> q.bool(bool -> {
			bool.must(must -> must.multiMatch(
					match -> match.query(text).fields("title^3", "description", "comments").fuzziness("AUTO")));
			if (reporterId != null) {
				bool.filter(filter -> filter.term(term -> term.field("reporterId").value(reporterId)));
			}
			return bool;
		})).withHighlightQuery(new HighlightQuery(highlight, TicketDocument.class)).withMaxResults(MAX_RESULTS).build();
	}

	private static List<String> highlights(SearchHit<TicketDocument> hit) {
		List<String> snippets = new ArrayList<>();
		for (String field : List.of("title", "description", "comments")) {
			snippets.addAll(hit.getHighlightField(field));
		}
		return snippets;
	}

	private TicketDocument toDocument(Ticket ticket) {
		List<String> commentBodies = comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId())
			.stream()
			.map(Comment::getBody)
			.toList();
		return new TicketDocument(ticket.getId(), ticket.getTitle(), ticket.getDescription(), commentBodies,
				ticket.getStatus().name(), ticket.getPriority().name(), ticket.getReporter().getId());
	}

}
