package com.example.helpdesk.assist;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

// One Bedrock call in the usage ledger (V6). Never changed after insert.
@Entity
public class AiUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private AiFeature feature;

	@Column(nullable = false, length = 100, updatable = false)
	private String model;

	@Column(nullable = false, updatable = false)
	private int inputTokens;

	@Column(nullable = false, updatable = false)
	private int outputTokens;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected AiUsage() {
	}

	AiUsage(AiCaller caller, String model, int inputTokens, int outputTokens) {
		this.userId = caller.userId();
		this.feature = caller.feature();
		this.model = model;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
	}

	public Long getUserId() {
		return userId;
	}

	public AiFeature getFeature() {
		return feature;
	}

	public String getModel() {
		return model;
	}

	public int getInputTokens() {
		return inputTokens;
	}

	public int getOutputTokens() {
		return outputTokens;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
