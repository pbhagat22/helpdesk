package com.example.helpdesk.ticket;

import java.time.Instant;

import com.example.helpdesk.user.UserResponse;

public record TicketResponse(Long id, String title, String description, TicketStatus status, Priority priority,
		Category category, UserResponse reporter, UserResponse assignee, Instant createdAt, Instant updatedAt) {

	public static TicketResponse from(Ticket ticket) {
		return new TicketResponse(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getStatus(),
				ticket.getPriority(), ticket.getCategory(), UserResponse.from(ticket.getReporter()),
				ticket.getAssignee() != null ? UserResponse.from(ticket.getAssignee()) : null, ticket.getCreatedAt(),
				ticket.getUpdatedAt());
	}

}
