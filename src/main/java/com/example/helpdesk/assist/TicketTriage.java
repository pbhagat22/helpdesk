package com.example.helpdesk.assist;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

// The AI's suggested priority and category for a ticket (one per ticket). Only a suggestion:
// staff apply it through the normal ticket update. Never changed after insert.
@Entity
public class TicketTriage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, updatable = false)
	private Long ticketId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private Category category;

	@Column(nullable = false, length = 500, updatable = false)
	private String reason;

	@Column(nullable = false, length = 100, updatable = false)
	private String model;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected TicketTriage() {
	}

	TicketTriage(Long ticketId, Priority priority, Category category, String reason, String model) {
		this.ticketId = ticketId;
		this.priority = priority;
		this.category = category;
		this.reason = reason;
		this.model = model;
	}

	public Long getTicketId() {
		return ticketId;
	}

	public Priority getPriority() {
		return priority;
	}

	public Category getCategory() {
		return category;
	}

	public String getReason() {
		return reason;
	}

	public String getModel() {
		return model;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
