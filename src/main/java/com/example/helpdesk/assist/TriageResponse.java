package com.example.helpdesk.assist;

import java.time.Instant;

import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;

public record TriageResponse(Priority priority, Category category, String reason, String model, Instant createdAt) {

	static TriageResponse from(TicketTriage triage) {
		return new TriageResponse(triage.getPriority(), triage.getCategory(), triage.getReason(), triage.getModel(),
				triage.getCreatedAt());
	}

}
