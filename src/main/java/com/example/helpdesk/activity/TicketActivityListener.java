package com.example.helpdesk.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.ticket.TicketEvent;
import com.example.helpdesk.ticket.TicketEventPublisher;

// Reads ticket events from Kafka and records them as ticket history.
//
// The consumer group "helpdesk-activity" is this listener's bookmark in the topic: Kafka
// remembers (as an "offset" per partition) how far the group has read. After a restart it
// carries on from there, and with several app instances the partitions are shared out so
// each event is handled by one of them.
@Component
class TicketActivityListener {

	private static final Logger log = LoggerFactory.getLogger(TicketActivityListener.class);

	private final TicketActivityRepository activities;

	TicketActivityListener(TicketActivityRepository activities) {
		this.activities = activities;
	}

	// Kafka guarantees "at least once": if the app stops after saving but before the offset
	// is committed, the same event comes again. Checking eventId makes processing idempotent
	// (doing it twice has the same effect as once); the unique constraint backs it up.
	@KafkaListener(topics = TicketEventPublisher.TOPIC, groupId = "helpdesk-activity")
	@Transactional
	void on(TicketEvent event) {
		if (activities.existsByEventId(event.eventId())) {
			log.debug("Skipping duplicate event {}", event.eventId());
			return;
		}
		activities.save(TicketActivity.from(event));
	}

}
