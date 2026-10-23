package com.example.helpdesk.eval;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.example.helpdesk.kb.ArticleRequest;
import com.example.helpdesk.kb.ArticleResponse;
import com.example.helpdesk.kb.KnowledgeBaseService;
import com.example.helpdesk.search.TicketSearchService;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.CreateCommentRequest;
import com.example.helpdesk.ticket.CreateTicketRequest;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.ticket.TicketStatus;
import com.example.helpdesk.ticket.UpdateTicketRequest;
import com.example.helpdesk.user.CreateUserRequest;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;
import com.example.helpdesk.user.UserService;

// Fills the eval database from eval/seed.json once, when the eval context starts: users,
// help articles and tickets (with comments, statuses and assignees), all through the normal
// services. The articles are embedded for real with Titan, so the evals include retrieval.
//
// Cases name things by email or title, never by id: lookups here turn them into ids, and
// resolve() replaces {ticket:<title>} in a case's text with that ticket's id.
public class EvalSeed implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EvalSeed.class);

	private static final Pattern TICKET_REF = Pattern.compile("\\{ticket:([^}]+)}");

	record Seed(List<SeedUser> users, List<ArticleRequest> articles, List<SeedTicket> tickets) {
	}

	record SeedUser(String email, String name, Role role) {
	}

	// status and assignee are optional; staff set them after creating the ticket, as in the UI
	record SeedTicket(String reporter, String title, String description, Priority priority, TicketStatus status,
			String assignee, List<SeedComment> comments) {
	}

	record SeedComment(String author, String body) {
	}

	private final UserService userService;

	private final TicketService ticketService;

	private final KnowledgeBaseService knowledgeBase;

	private final TicketSearchService ticketSearch;

	private final Map<String, HelpdeskUserDetails> users = new HashMap<>();

	private final Map<String, Long> tickets = new HashMap<>();

	private final Map<String, Long> articles = new HashMap<>();

	public EvalSeed(UserService userService, TicketService ticketService, KnowledgeBaseService knowledgeBase,
			TicketSearchService ticketSearch) {
		this.userService = userService;
		this.ticketService = ticketService;
		this.knowledgeBase = knowledgeBase;
		this.ticketSearch = ticketSearch;
	}

	@Override
	public void run(ApplicationArguments args) {
		Seed seed = EvalFiles.read("eval/seed.json", Seed.class);
		for (SeedUser user : seed.users()) {
			UserResponse created = userService
				.create(new CreateUserRequest(user.email(), user.name(), user.role(), "eval-password"));
			users.put(created.email(), new HelpdeskUserDetails(created.id(), created.email(), null, created.role()));
		}
		HelpdeskUserDetails agent = agent();
		for (ArticleRequest article : seed.articles()) {
			ArticleResponse created = knowledgeBase.create(article, agent);
			// The article is saved even when embedding fails; without chunks, every KB-based
			// case would fail for a reason that has nothing to do with the model, so stop here.
			if (created.chunkCount() == 0) {
				throw new IllegalStateException("Embedding failed for article '" + created.title()
						+ "'. Are AWS credentials set (AWS_PROFILE=helpdesk)? See the log above.");
			}
			articles.put(created.title(), created.id());
		}
		for (SeedTicket ticket : seed.tickets()) {
			Long id = ticketService.create(
					new CreateTicketRequest(ticket.title(), ticket.description(), ticket.priority()),
					user(ticket.reporter()).getId())
				.id();
			if (ticket.comments() != null) {
				for (SeedComment comment : ticket.comments()) {
					ticketService.addComment(id, new CreateCommentRequest(comment.body()), user(comment.author()));
				}
			}
			if (ticket.status() != null || ticket.assignee() != null) {
				Long assigneeId = ticket.assignee() != null ? user(ticket.assignee()).getId() : null;
				ticketService.update(id, new UpdateTicketRequest(ticket.status(), null, assigneeId, null), agent);
			}
			tickets.put(ticket.title(), id);
		}
		// The search indexer is a Kafka listener, which doesn't run in evals (see EvalTest)
		ticketSearch.reindexAll();
		log.info("Eval data seeded: {} users, {} articles, {} tickets", users.size(), articles.size(), tickets.size());
	}

	// Unknown names fail loudly, so a typo in a case file can't turn into a silently failing case
	public HelpdeskUserDetails user(String email) {
		return lookup(users, email, "user");
	}

	// The first agent in seed.json: who the evals act as
	public HelpdeskUserDetails agent() {
		return users.values()
			.stream()
			.filter(user -> user.getRole() == Role.AGENT)
			.min(Comparator.comparing(HelpdeskUserDetails::getId))
			.orElseThrow();
	}

	public Long ticketId(String title) {
		return lookup(tickets, title, "ticket");
	}

	public Long articleId(String title) {
		return lookup(articles, title, "article");
	}

	// "Who reported {ticket:Charged twice this month}?" -> "Who reported 1?"
	public String resolve(String text) {
		Matcher matcher = TICKET_REF.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(sb, String.valueOf(ticketId(matcher.group(1))));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static <T> T lookup(Map<String, T> map, String key, String kind) {
		T value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException("No " + kind + " '" + key + "' in eval/seed.json");
		}
		return value;
	}

}
