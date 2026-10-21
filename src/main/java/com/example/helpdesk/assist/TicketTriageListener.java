package com.example.helpdesk.assist;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.helpdesk.ticket.TicketEvent;
import com.example.helpdesk.ticket.TicketEventPublisher;
import com.example.helpdesk.ticket.TicketEventType;

// A third consumer group on the ticket events, next to history and search. It only cares
// about new tickets. Its own group means a slow or unavailable model delays nothing else.
//
// If triage fails (Bedrock down, an invalid answer), the error handler retries twice and then
// skips the event: that ticket simply gets no suggestion. A new group starts at the oldest
// event still in the topic (auto-offset-reset=earliest), so the first start also triages
// existing tickets once; already-triaged ones are skipped.
@Component
class TicketTriageListener {

	private final TicketTriageService triageService;

	TicketTriageListener(TicketTriageService triageService) {
		this.triageService = triageService;
	}

	@KafkaListener(topics = TicketEventPublisher.TOPIC, groupId = "helpdesk-triage")
	void on(TicketEvent event) {
		if (event.type() == TicketEventType.CREATED) {
			triageService.triage(event.ticketId());
		}
	}

}
