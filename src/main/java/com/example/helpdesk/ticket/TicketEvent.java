package com.example.helpdesk.ticket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Something that happened to a ticket, published to Kafka as JSON. It's a fact about the
// past ("STATUS_CHANGED from OPEN to IN_PROGRESS"), not a command, so it's never changed
// after publishing. Consumers decide for themselves what to do with it.
//
// eventId lets consumers recognise an event they've already processed (Kafka may deliver
// the same message more than once). details holds the type-specific facts, e.g.
// {"from": "OPEN", "to": "IN_PROGRESS"}.
public record TicketEvent(UUID eventId, TicketEventType type, Long ticketId, Long actorId, Instant occurredAt,
		Map<String, String> details) {

	public static TicketEvent of(TicketEventType type, Long ticketId, Long actorId, Map<String, String> details) {
		return new TicketEvent(UUID.randomUUID(), type, ticketId, actorId, Instant.now(), details);
	}

}
