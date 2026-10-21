package com.example.helpdesk.ticket;

// Partial update: a null field means "leave unchanged".
public record UpdateTicketRequest(TicketStatus status, Priority priority, Long assigneeId, Category category) {
}
