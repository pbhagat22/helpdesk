package com.example.helpdesk.activity;

import java.time.Instant;
import java.util.Map;

import com.example.helpdesk.ticket.TicketEventType;
import com.example.helpdesk.user.UserResponse;

// actor is null if the user no longer exists (history outlives accounts).
public record ActivityResponse(Long id, TicketEventType type, UserResponse actor, Map<String, String> details,
		Instant occurredAt) {
}
