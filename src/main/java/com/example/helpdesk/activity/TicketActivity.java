package com.example.helpdesk.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.helpdesk.ticket.TicketEvent;
import com.example.helpdesk.ticket.TicketEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

// One entry in a ticket's history, stored from a TicketEvent. Never updated after insert.
@Entity
public class TicketActivity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, updatable = false)
	private UUID eventId;

	@Column(nullable = false, updatable = false)
	private Long ticketId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private TicketEventType type;

	@Column(nullable = false, updatable = false)
	private Long actorId;

	// Hibernate converts the map to and from JSON in a Postgres jsonb column.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private Map<String, String> details;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	protected TicketActivity() {
	}

	static TicketActivity from(TicketEvent event) {
		TicketActivity activity = new TicketActivity();
		activity.eventId = event.eventId();
		activity.ticketId = event.ticketId();
		activity.type = event.type();
		activity.actorId = event.actorId();
		activity.details = event.details();
		activity.occurredAt = event.occurredAt();
		return activity;
	}

	public Long getId() {
		return id;
	}

	public UUID getEventId() {
		return eventId;
	}

	public Long getTicketId() {
		return ticketId;
	}

	public TicketEventType getType() {
		return type;
	}

	public Long getActorId() {
		return actorId;
	}

	public Map<String, String> getDetails() {
		return details;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
