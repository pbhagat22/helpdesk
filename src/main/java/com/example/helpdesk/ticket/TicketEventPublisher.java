package com.example.helpdesk.ticket;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

// Sends TicketEvents to Kafka, but only once the database change behind them is committed.
//
// TicketService publishes events with Spring's in-process ApplicationEventPublisher during its
// transaction. @TransactionalEventListener holds each one until the transaction commits (and
// drops it on rollback), so Kafka never announces a change that didn't happen.
//
// Known gap: if the app crashes after the commit but before Kafka acknowledges the send, the
// event is lost. Closing that gap needs the "transactional outbox" pattern (events stored in
// the database in the same transaction, then relayed to Kafka); fine to skip for now.
@Configuration
public class TicketEventPublisher {

	public static final String TOPIC = "helpdesk.ticket-events";

	private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

	private final KafkaTemplate<String, TicketEvent> kafka;

	public TicketEventPublisher(KafkaTemplate<String, TicketEvent> kafka) {
		this.kafka = kafka;
	}

	// Creates the topic at startup if it doesn't exist. Partitions let consumers share the
	// work; one copy (replica) is all a single-broker dev setup can hold.
	@Bean
	NewTopic ticketEventsTopic() {
		return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
	}

	@TransactionalEventListener
	void publish(TicketEvent event) {
		// The key decides the partition: all events for one ticket share a key, so they land
		// in the same partition and consumers see them in the order they happened.
		String key = event.ticketId().toString();
		try {
			kafka.send(TOPIC, key, event).whenComplete((result, ex) -> {
				if (ex != null) {
					log.warn("Failed to publish {} for ticket {}", event.type(), event.ticketId(), ex);
				}
			});
		}
		catch (RuntimeException ex) {
			// The ticket change is already committed; losing the event shouldn't fail the request.
			log.warn("Failed to publish {} for ticket {}", event.type(), event.ticketId(), ex);
		}
	}

}
