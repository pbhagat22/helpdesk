package com.example.helpdesk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

// A stand-in for Amazon Bedrock in tests that run the whole application. Every created ticket
// is triaged in the background, and the tests must never reach the real Bedrock: CI has no
// AWS credentials, and on a developer machine it would cost money. The canned answers
// depend on which schema the request asks for; agent requests (with tools) first get a
// list_tickets call, then a text answer once the tool result is in the conversation.
@TestConfiguration(proxyBeanMethods = false)
public class FakeBedrockConfiguration {

	public static final String TRIAGE_ANSWER = """
			{"priority": "HIGH", "category": "BILLING", "reason": "Fake triage for tests."}""";

	public static final String ASSIST_ANSWER = """
			{"summary": "Fake summary.", "suggestedReply": "Fake reply."}""";

	public static final String AGENT_ANSWER = "Fake agent answer.";

	// @Primary: used instead of AiConfig's real client wherever a BedrockRuntimeClient is injected
	@Bean
	@Primary
	BedrockRuntimeClient fakeBedrockRuntimeClient() {
		BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
		given(client.converse(any(ConverseRequest.class))).willAnswer(invocation -> {
			ConverseRequest request = invocation.getArgument(0);
			if (request.toolConfig() != null) {
				return agentTurn(request);
			}
			String schemaName = request.outputConfig().textFormat().structure().jsonSchema().name();
			String answer = schemaName.equals("ticket_triage") ? TRIAGE_ANSWER : ASSIST_ANSWER;
			return ConverseResponse.builder()
				.stopReason(StopReason.END_TURN)
				.usage(usage -> usage.inputTokens(100).outputTokens(20).totalTokens(120))
				.output(ConverseOutput.fromMessage(
						Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.fromText(answer)).build()))
				.build();
		});
		return client;
	}

	private static ConverseResponse agentTurn(ConverseRequest request) {
		boolean toolResultReceived = request.messages()
			.stream()
			.flatMap(message -> message.content().stream())
			.anyMatch(block -> block.toolResult() != null);
		ContentBlock content = toolResultReceived ? ContentBlock.fromText(AGENT_ANSWER)
				: ContentBlock.fromToolUse(ToolUseBlock.builder()
					.toolUseId("fake-1")
					.name("list_tickets")
					.input(Document.fromMap(Map.of("status", Document.fromString("OPEN"))))
					.build());
		return ConverseResponse.builder()
			.stopReason(toolResultReceived ? StopReason.END_TURN : StopReason.TOOL_USE)
			.usage(usage -> usage.inputTokens(100).outputTokens(20).totalTokens(120))
			.output(ConverseOutput
				.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).content(content).build()))
			.build();
	}

}
