package com.example.helpdesk.assist;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.helpdesk.common.TooManyRequestsException;

// Daily AI limits, checked before every Bedrock call against the usage ledger:
// 1. A global cost cap for all features together (background triage included).
// 2. A token budget per user, by role. Anyone can sign up as a customer, so without it one
//    account could run up the bill with KB searches.
//
// Soft limits: usage is checked before a call, so one call can go over by its own size, and
// two calls at the same moment can both pass. That's normal for quotas and needs no locking.
// Limits reset at 00:00 UTC, the same days as the usage report.
@Component
public class AiBudget {

	private final AiUsageQueries queries;

	private final AiPricing pricing;

	private final AiBudgetProperties properties;

	private final Clock clock;

	@Autowired
	public AiBudget(AiUsageQueries queries, AiPricing pricing, AiBudgetProperties properties) {
		this(queries, pricing, properties, Clock.systemUTC());
	}

	AiBudget(AiUsageQueries queries, AiPricing pricing, AiBudgetProperties properties, Clock clock) {
		this.queries = queries;
		this.pricing = pricing;
		this.properties = properties;
		this.clock = clock;
	}

	// Throws TooManyRequestsException (429) if a limit is reached.
	public void check(AiCaller caller) {
		Instant now = clock.instant();
		Instant midnight = LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
		Duration untilReset = Duration.between(now, midnight.plus(Duration.ofDays(1)));

		BigDecimal spentToday = queries.byModel(midnight)
			.stream()
			.map(row -> pricing.cost(row.model(), row.inputTokens(), row.outputTokens()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (spentToday.compareTo(properties.dailyCostUsd()) >= 0) {
			throw new TooManyRequestsException(
					"The AI features have reached today's spending limit; they're available again after 00:00 UTC",
					untilReset);
		}

		if (caller.userId() != null
				&& queries.userTokens(caller.userId(), midnight) >= properties.dailyTokens(caller.role())) {
			throw new TooManyRequestsException("You've used your AI allowance for today; it resets at 00:00 UTC",
					untilReset);
		}
	}

}
