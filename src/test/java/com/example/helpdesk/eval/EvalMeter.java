package com.example.helpdesk.eval;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.example.helpdesk.assist.AiPricing;
import com.example.helpdesk.assist.AiProperties;

// Measures what an eval run cost, from the usage ledger (ai_usage) that every Bedrock call
// writes to: remember the highest row id before the run (mark), then add up the rows after
// it (since). Evals run one call at a time and Kafka listeners are off, so nothing else
// writes rows in between.
public class EvalMeter {

	public record Usage(long inputTokens, long outputTokens, BigDecimal costUsd) {

		public static final Usage ZERO = new Usage(0, 0, BigDecimal.ZERO);

		public Usage plus(Usage other) {
			return new Usage(inputTokens + other.inputTokens, outputTokens + other.outputTokens,
					costUsd.add(other.costUsd));
		}

	}

	private final JdbcClient jdbc;

	private final AiPricing pricing;

	private final AiProperties ai;

	public EvalMeter(JdbcClient jdbc, AiPricing pricing, AiProperties ai) {
		this.jdbc = jdbc;
		this.pricing = pricing;
		this.ai = ai;
	}

	public long mark() {
		return jdbc.sql("select coalesce(max(id), 0) from ai_usage").query(Long.class).single();
	}

	// Priced per model, because one run can use several (e.g. assist: Titan for the KB search,
	// then the LLM)
	public Usage since(long mark) {
		return jdbc.sql("""
				select model, sum(input_tokens) as input, sum(output_tokens) as output
				from ai_usage where id > :mark group by model""")
			.param("mark", mark)
			.query((rs, rowNum) -> new Usage(rs.getLong("input"), rs.getLong("output"),
					pricing.cost(rs.getString("model"), rs.getLong("input"), rs.getLong("output"))))
			.list()
			.stream()
			.reduce(Usage.ZERO, Usage::plus);
	}

	// Which models were evaluated, for the report: comparing models means rerunning with
	// e.g. -Dhelpdesk.ai.model=...
	public Map<String, String> models() {
		Map<String, String> models = new LinkedHashMap<>();
		models.put("model", ai.model());
		models.put("agentModel", ai.agentModel());
		models.put("embeddingModel", ai.embeddingModel());
		return models;
	}

}
