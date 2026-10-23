package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.example.helpdesk.common.TooManyRequestsException;
import com.example.helpdesk.user.Role;

class AiBudgetTests {

	private static final Instant MIDNIGHT = Instant.parse("2026-10-22T00:00:00Z");

	private final AiUsageQueries queries = mock(AiUsageQueries.class);

	// $1 per million tokens, in and out
	private final AiPricing pricing = new AiPricing(
			Map.of("model", new AiPricing.ModelPrice(BigDecimal.ONE, BigDecimal.ONE)));

	// Customers 100 tokens a day, staff 1,000; everyone together $1 a day
	private final AiBudget budget = new AiBudget(queries, pricing,
			new AiBudgetProperties(100, 1_000, 1_000, BigDecimal.ONE),
			Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC));

	private final AiCaller customer = new AiCaller(AiFeature.KB_SEARCH, 1L, Role.CUSTOMER);

	private final AiCaller agent = new AiCaller(AiFeature.AGENT, 2L, Role.AGENT);

	@Test
	void underBudgetPasses() {
		given(queries.byModel(MIDNIGHT)).willReturn(List.of());
		given(queries.userTokens(1L, MIDNIGHT)).willReturn(99L);

		assertThatNoException().isThrownBy(() -> budget.check(customer));
	}

	@Test
	void userOverTheirRolesBudgetGets429UntilMidnightUtc() {
		given(queries.byModel(MIDNIGHT)).willReturn(List.of());
		given(queries.userTokens(1L, MIDNIGHT)).willReturn(100L);

		assertThatThrownBy(() -> budget.check(customer)).isInstanceOf(TooManyRequestsException.class)
			.hasMessageContaining("allowance")
			.extracting(ex -> ((TooManyRequestsException) ex).getRetryAfter())
			.isEqualTo(Duration.ofHours(9));
	}

	@Test
	void theBudgetDependsOnTheRole() {
		given(queries.byModel(MIDNIGHT)).willReturn(List.of());
		given(queries.userTokens(2L, MIDNIGHT)).willReturn(500L);

		// 500 tokens would be over a customer's budget, but not over an agent's
		assertThatNoException().isThrownBy(() -> budget.check(agent));
	}

	@Test
	void globalCostCapStopsEveryoneIncludingBackgroundWork() {
		// 600,000 + 400,000 tokens at $1 per million = $1, the cap
		given(queries.byModel(MIDNIGHT)).willReturn(List.of(new AiUsageQueries.ModelRow("model", 600_000, 400_000)));

		assertThatThrownBy(() -> budget.check(AiCaller.background(AiFeature.TRIAGE)))
			.isInstanceOf(TooManyRequestsException.class)
			.hasMessageContaining("spending limit");
		assertThatThrownBy(() -> budget.check(agent)).isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void backgroundWorkHasNoUserBudget() {
		given(queries.byModel(MIDNIGHT)).willReturn(List.of());

		budget.check(AiCaller.background(AiFeature.TRIAGE));

		then(queries).should(never()).userTokens(ArgumentMatchers.anyLong(), ArgumentMatchers.any());
	}

	@Test
	void retryAfterIsNeverZero() {
		AiBudget justBeforeMidnight = new AiBudget(queries, pricing,
				new AiBudgetProperties(100, 1_000, 1_000, BigDecimal.ONE),
				Clock.fixed(Instant.parse("2026-10-22T23:59:59Z"), ZoneOffset.UTC));
		given(queries.byModel(Instant.parse("2026-10-22T00:00:00Z"))).willReturn(List.of());
		given(queries.userTokens(1L, MIDNIGHT)).willReturn(100L);

		assertThatThrownBy(() -> justBeforeMidnight.check(customer))
			.extracting(ex -> ((TooManyRequestsException) ex).getRetryAfter())
			.satisfies(retryAfter -> assertThat(retryAfter).isEqualTo(Duration.ofSeconds(1)));
	}

}
