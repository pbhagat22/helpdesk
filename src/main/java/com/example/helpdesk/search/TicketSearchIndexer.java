package com.example.helpdesk.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.helpdesk.ticket.TicketEvent;
import com.example.helpdesk.ticket.TicketEventPublisher;

// Keeps the search index in sync with the tickets, driven by the same Kafka events as the
// ticket history, but in its own consumer group ("helpdesk-search"). Each group reads the
// whole topic independently, at its own pace: if Elasticsearch is slow or down, the history
// keeps updating, and vice versa.
//
// The event is only a trigger ("ticket 5 changed"): the indexer re-reads the ticket's
// current state from Postgres instead of applying the event's details. That way the index
// can't drift, and events arriving twice or out of order do no harm.
@Component
class TicketSearchIndexer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TicketSearchIndexer.class);

	private final TicketSearchService searchService;

	TicketSearchIndexer(TicketSearchService searchService) {
		this.searchService = searchService;
	}

	// If indexing fails (Elasticsearch down), the error handler retries twice, then skips the
	// event; POST /api/admin/search/reindex brings the index back in line afterwards.
	@KafkaListener(topics = TicketEventPublisher.TOPIC, groupId = "helpdesk-search")
	void on(TicketEvent event) {
		searchService.index(event.ticketId());
	}

	// Create the index at startup when possible, but don't stop the app if Elasticsearch is
	// down: search will retry creating it on first use.
	@Override
	public void run(ApplicationArguments args) {
		try {
			searchService.ensureIndex();
		}
		catch (RuntimeException ex) {
			log.warn("Elasticsearch unavailable at startup; search will be unavailable until it's reachable", ex);
		}
	}

}
