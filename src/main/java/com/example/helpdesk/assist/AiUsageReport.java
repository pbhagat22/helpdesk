package com.example.helpdesk.assist;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// GET /api/admin/ai/usage. Costs are estimates in USD from configured prices (AiPricing);
// models without a price count as 0 and are listed in unpricedModels.
public record AiUsageReport(Instant since, BigDecimal estimatedCostUsd, List<String> unpricedModels,
		List<DayTotal> days, List<UserTotal> topUsers) {

	public record DayTotal(LocalDate day, AiFeature feature, String model, long calls, long inputTokens,
			long outputTokens, BigDecimal estimatedCostUsd) {
	}

	// email is null if the user no longer exists
	public record UserTotal(long userId, String email, long calls, long inputTokens, long outputTokens,
			BigDecimal estimatedCostUsd) {
	}

}
