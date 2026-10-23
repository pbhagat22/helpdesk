package com.example.helpdesk.assist;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

// The usage ledger: every Bedrock call is recorded with its tokens, so admins can see who and
// what spends the AI budget, and the budgets can be enforced.
//
// No class-level @Transactional: record() is called right after a model call, outside any
// transaction, and save() runs in its own; the report's queries have their own too.
@Service
public class AiUsageService {

	private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

	static final int TOP_USERS = 10;

	private final AiUsageRepository repository;

	private final AiUsageQueries queries;

	private final AiPricing pricing;

	private final Clock clock;

	@Autowired
	public AiUsageService(AiUsageRepository repository, AiUsageQueries queries, AiPricing pricing) {
		this(repository, queries, pricing, Clock.systemUTC());
	}

	AiUsageService(AiUsageRepository repository, AiUsageQueries queries, AiPricing pricing, Clock clock) {
		this.repository = repository;
		this.queries = queries;
		this.pricing = pricing;
		this.clock = clock;
	}

	// Called as soon as Bedrock has answered, before the answer is checked: an answer that's
	// cut off or invalid is billed all the same. If the row can't be written, the user still
	// gets the answer that was already paid for, so this only logs.
	public void record(AiCaller caller, String model, int inputTokens, int outputTokens) {
		try {
			repository.save(new AiUsage(caller, model, inputTokens, outputTokens));
		}
		catch (DataAccessException ex) {
			log.warn("Couldn't record AI usage for {} ({} in / {} out tokens)", caller, inputTokens, outputTokens, ex);
		}
	}

	// The last `days` UTC days, today included (days = 1: today only).
	public AiUsageReport report(int days) {
		Instant since = LocalDate.now(clock).minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();

		List<AiUsageReport.DayTotal> dayTotals = queries.byDay(since)
			.stream()
			.map(row -> new AiUsageReport.DayTotal(row.day(), row.feature(), row.model(), row.calls(),
					row.inputTokens(), row.outputTokens(), pricing.cost(row.model(), row.inputTokens(), row.outputTokens())))
			.toList();
		BigDecimal total = dayTotals.stream()
			.map(AiUsageReport.DayTotal::estimatedCostUsd)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		List<String> unpriced = dayTotals.stream()
			.map(AiUsageReport.DayTotal::model)
			.filter(model -> !pricing.knows(model))
			.distinct()
			.sorted()
			.toList();

		return new AiUsageReport(since, total, unpriced, dayTotals, topUsers(queries.byUser(since)));
	}

	// The query gives one row per user and model; add up each user's models, most expensive first.
	private List<AiUsageReport.UserTotal> topUsers(List<AiUsageQueries.UserRow> rows) {
		Map<Long, AiUsageReport.UserTotal> perUser = new LinkedHashMap<>();
		for (AiUsageQueries.UserRow row : rows) {
			AiUsageReport.UserTotal sum = perUser.getOrDefault(row.userId(),
					new AiUsageReport.UserTotal(row.userId(), row.email(), 0, 0, 0, BigDecimal.ZERO));
			perUser.put(row.userId(), new AiUsageReport.UserTotal(row.userId(), row.email(), sum.calls() + row.calls(),
					sum.inputTokens() + row.inputTokens(), sum.outputTokens() + row.outputTokens(), sum.estimatedCostUsd()
						.add(pricing.cost(row.model(), row.inputTokens(), row.outputTokens()))));
		}
		return perUser.values()
			.stream()
			.sorted(Comparator.comparing(AiUsageReport.UserTotal::estimatedCostUsd)
				.thenComparing(AiUsageReport.UserTotal::inputTokens)
				.reversed())
			.limit(TOP_USERS)
			.toList();
	}

}
