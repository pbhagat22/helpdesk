package com.example.helpdesk.assist;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// helpdesk.ai.* (or HELPDESK_AI_REGION, HELPDESK_AI_MODEL, HELPDESK_AI_TIMEOUT).
// The model is any Bedrock model ID that supports structured output through the Converse
// API, e.g. qwen.qwen3-32b-v1:0 or openai.gpt-oss-120b-1:0. Switching models needs no code change.
@ConfigurationProperties("helpdesk.ai")
public record AiProperties(@DefaultValue("us-east-2") String region,
		@DefaultValue("qwen.qwen3-32b-v1:0") String model, @DefaultValue("60s") Duration timeout) {
}
