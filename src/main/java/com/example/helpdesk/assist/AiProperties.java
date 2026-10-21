package com.example.helpdesk.assist;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// helpdesk.ai.* (or HELPDESK_AI_REGION, HELPDESK_AI_MODEL, HELPDESK_AI_AGENT_MODEL,
// HELPDESK_AI_EMBEDDING_MODEL, HELPDESK_AI_TIMEOUT).
// Models are Bedrock model IDs used through the Converse API, so switching needs no code change.
// - model: the one-shot features (assist, triage). Needs structured output support; a small,
//   fast model is enough.
// - agentModel: the agent (several tool calls per question). Needs tool use support; a larger
//   model follows multi-step instructions more reliably, at the cost of speed.
// - embeddingModel: turns text into vectors for the knowledge base. Changing it means
//   re-embedding every article (POST /api/admin/kb/reindex): vectors from different models
//   can't be compared.
@ConfigurationProperties("helpdesk.ai")
public record AiProperties(@DefaultValue("us-east-2") String region,
		@DefaultValue("qwen.qwen3-32b-v1:0") String model,
		@DefaultValue("qwen.qwen3-235b-a22b-2507-v1:0") String agentModel,
		@DefaultValue("amazon.titan-embed-text-v2:0") String embeddingModel, @DefaultValue("60s") Duration timeout) {
}
