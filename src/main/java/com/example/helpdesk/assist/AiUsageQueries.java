package com.example.helpdesk.assist;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// Aggregations over the ai_usage ledger, in plain SQL (group by is what SQL is good at).
// Grouped by model too, because the price depends on the model. Days are UTC days.
@Repository
public class AiUsageQueries {

	public record DayRow(LocalDate day, AiFeature feature, String model, long calls, long inputTokens,
			long outputTokens) {
	}

	public record UserRow(long userId, String email, String model, long calls, long inputTokens, long outputTokens) {
	}

	private final JdbcClient jdbc;

	public AiUsageQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<DayRow> byDay(Instant since) {
		return jdbc.sql("""
				select cast(created_at at time zone 'UTC' as date) as day, feature, model, count(*) as calls,
				       sum(input_tokens) as input_tokens, sum(output_tokens) as output_tokens
				from ai_usage
				where created_at >= :since
				group by day, feature, model
				order by day desc, feature, model
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new DayRow(rs.getObject("day", LocalDate.class), AiFeature.valueOf(rs.getString("feature")),
					rs.getString("model"), rs.getLong("calls"), rs.getLong("input_tokens"), rs.getLong("output_tokens")))
			.list();
	}

	// Per user and model; background work (no user) is left out. A left join, because the
	// ledger has no foreign key: a deleted user's usage still counts (with no email).
	@Transactional(readOnly = true)
	public List<UserRow> byUser(Instant since) {
		return jdbc.sql("""
				select a.user_id, u.email, a.model, count(*) as calls,
				       sum(a.input_tokens) as input_tokens, sum(a.output_tokens) as output_tokens
				from ai_usage a
				left join users u on u.id = a.user_id
				where a.created_at >= :since and a.user_id is not null
				group by a.user_id, u.email, a.model
				""")
			.param("since", Timestamp.from(since))
			.query((rs, row) -> new UserRow(rs.getLong("user_id"), rs.getString("email"), rs.getString("model"),
					rs.getLong("calls"), rs.getLong("input_tokens"), rs.getLong("output_tokens")))
			.list();
	}

}
