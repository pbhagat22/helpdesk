package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.example.helpdesk.user.Role;

class AiUsageServiceTests {

	private final AiUsageRepository repository = mock(AiUsageRepository.class);

	private final AiUsageQueries queries = mock(AiUsageQueries.class);

	// $1 per million input tokens and $2 per million output tokens: easy to check by hand
	private final AiPricing pricing = new AiPricing(
			Map.of("small", new AiPricing.ModelPrice(BigDecimal.ONE, BigDecimal.TWO)));

	private final AiUsageService service = new AiUsageService(repository, queries, pricing,
			Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC));

	private final LocalDate today = LocalDate.parse("2026-10-22");

	@Test
	void reportStartsAtMidnightUtcOfTheFirstDay() {
		Instant since = Instant.parse("2026-10-16T00:00:00Z");
		given(queries.byDay(since)).willReturn(List.of());
		given(queries.byUser(since)).willReturn(List.of());

		assertThat(service.report(7).since()).isEqualTo(since);
	}

	@Test
	void reportPricesEachRowAndListsUnpricedModels() {
		Instant since = Instant.parse("2026-10-22T00:00:00Z");
		given(queries.byDay(since)).willReturn(List.of(
				new AiUsageQueries.DayRow(today, AiFeature.ASSIST, "small", 3, 1_000_000, 500_000),
				new AiUsageQueries.DayRow(today, AiFeature.AGENT, "mystery", 1, 1_000_000, 0)));
		given(queries.byUser(since)).willReturn(List.of());

		AiUsageReport report = service.report(1);

		assertThat(report.days()).extracting(AiUsageReport.DayTotal::estimatedCostUsd)
			.containsExactly(new BigDecimal("2.000000"), BigDecimal.ZERO);
		// The unpriced model counts as 0 and is named, so nobody mistakes it for free
		assertThat(report.estimatedCostUsd()).isEqualByComparingTo("2");
		assertThat(report.unpricedModels()).containsExactly("mystery");
	}

	@Test
	void topUsersAddUpTheirModelsMostExpensiveFirst() {
		Instant since = Instant.parse("2026-10-22T00:00:00Z");
		given(queries.byDay(since)).willReturn(List.of());
		given(queries.byUser(since)).willReturn(List.of(
				new AiUsageQueries.UserRow(1, "alice@example.com", "small", 1, 100_000, 0),
				new AiUsageQueries.UserRow(2, "bob@example.com", "small", 2, 1_000_000, 0),
				new AiUsageQueries.UserRow(2, "bob@example.com", "mystery", 1, 50, 0)));

		assertThat(service.report(1).topUsers()).containsExactly(
				new AiUsageReport.UserTotal(2, "bob@example.com", 3, 1_000_050, 0, new BigDecimal("1.000000")),
				new AiUsageReport.UserTotal(1, "alice@example.com", 1, 100_000, 0, new BigDecimal("0.100000")));
	}

	@Test
	void recordSavesARow() {
		service.record(AiCaller.background(AiFeature.TRIAGE), "small", 10, 5);

		then(repository).should().save(any(AiUsage.class));
	}

	@Test
	void recordOnlyLogsWhenTheDatabaseFails() {
		given(repository.save(any(AiUsage.class)))
			.willThrow(new DataAccessResourceFailureException("db down"));

		// No exception: the model's answer was already paid for, so the user still gets it
		service.record(new AiCaller(AiFeature.ASSIST, 2L, Role.AGENT), "small", 10, 5);
	}

}
