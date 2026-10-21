package com.example.helpdesk.agent.tools;

import java.time.Instant;

import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketStatus;

// A ticket as the agent sees it in lists: only what's needed to answer questions about it.
// Names instead of nested user objects keep the JSON (and the token bill) small.
public record TicketSummary(Long id, String title, TicketStatus status, Priority priority, Category category,
		String reporter, String assignee, Instant createdAt, Instant updatedAt) {

	public static TicketSummary from(TicketResponse ticket) {
		return new TicketSummary(ticket.id(), ticket.title(), ticket.status(), ticket.priority(), ticket.category(),
				ticket.reporter().name(), ticket.assignee() != null ? ticket.assignee().name() : null,
				ticket.createdAt(), ticket.updatedAt());
	}

}
