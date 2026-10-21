package com.example.helpdesk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

// A stand-in for Amazon Bedrock in tests that run the whole application. Every created ticket
// is triaged in the background, and the tests must never reach the real Bedrock: CI has no
// AWS credentials, and on a developer machine it would cost money. The canned answers
// depend on which schema the request asks for.
@TestConfiguration(proxyBeanMethods = false)
public class FakeBedrockConfiguration {

	public static final String TRIAGE_ANSWER = """
			{"priority": "HIGH", "category": "BILLING", "reason": "Fake triage for tests."}""";

	public static final String ASSIST_ANSWER = """
			{"summary": "Fake summary.", "suggestedReply": "Fake reply."}""";

	// @Primary: used instead of AiConfig's real client wherever a BedrockRuntimeClient is injected
	@Bean
	@Primary
	BedrockRuntimeClient fakeBedrockRuntimeClient() {
		BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
		given(client.converse(any(ConverseRequest.class))).willAnswer(invocation -> {
			ConverseRequest request = invocation.getArgument(0);
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

}
