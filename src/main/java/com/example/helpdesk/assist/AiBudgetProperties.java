package com.example.helpdesk.assist;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.example.helpdesk.user.Role;

// helpdesk.ai.budget.*: daily limits on AI use, per UTC day (AiBudget).
// - <role>-daily-tokens: input + output tokens one user of that role may use per day. A KB
//   search is 10-30 tokens; an agent question 5,000-50,000.
// - daily-cost-usd: what all AI features together may cost per day (estimated with AiPricing),
//   the kill switch against a runaway bill. Models without a price don't count towards it.
@ConfigurationProperties("helpdesk.ai.budget")
public record AiBudgetProperties(@DefaultValue("10000") long customerDailyTokens,
		@DefaultValue("1000000") long agentDailyTokens, @DefaultValue("1000000") long adminDailyTokens,
		@DefaultValue("1.00") BigDecimal dailyCostUsd) {

	public long dailyTokens(Role role) {
		return switch (role) {
			case CUSTOMER -> customerDailyTokens;
			case AGENT -> agentDailyTokens;
			case ADMIN -> adminDailyTokens;
		};
	}

}
