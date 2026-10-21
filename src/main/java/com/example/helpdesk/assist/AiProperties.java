package com.example.helpdesk.assist;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// helpdesk.ai.* (or HELPDESK_AI_REGION, HELPDESK_AI_MODEL, HELPDESK_AI_AGENT_MODEL, HELPDESK_AI_TIMEOUT).
// Models are Bedrock model IDs used through the Converse API, so switching needs no code change.
// - model: the one-shot features (assist, triage). Needs structured output support; a small,
//   fast model is enough.
// - agentModel: the agent (several tool calls per question). Needs tool use support; a larger
//   model follows multi-step instructions more reliably, at the cost of speed.
@ConfigurationProperties("helpdesk.ai")
public record AiProperties(@DefaultValue("us-east-2") String region,
		@DefaultValue("qwen.qwen3-32b-v1:0") String model,
		@DefaultValue("qwen.qwen3-235b-a22b-2507-v1:0") String agentModel, @DefaultValue("60s") Duration timeout) {
}
