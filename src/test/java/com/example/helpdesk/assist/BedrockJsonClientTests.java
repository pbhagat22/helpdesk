package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.helpdesk.common.ServiceUnavailableException;
import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import tools.jackson.databind.json.JsonMapper;

class BedrockJsonClientTests {

	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);

	private final BedrockJsonClient client = new BedrockJsonClient(bedrock,
			new AiProperties("us-east-2", "test-model", "test-agent-model", "test-embedding-model", Duration.ofSeconds(5)), JsonMapper.builder().build());

	private AssistResponse askForAssist() {
		return client.ask("test", "system prompt", "user prompt", "ticket_assist", TicketAssistService.SCHEMA,
				AssistResponse.class);
	}

	@Test
	void sendsPromptsAndSchemaAndParsesTheAnswer() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(response(StopReason.END_TURN,
				"{\"summary\": \"Printer smoking.\", \"suggestedReply\": \"Please keep it unplugged.\"}"));

		assertThat(askForAssist()).isEqualTo(new AssistResponse("Printer smoking.", "Please keep it unplugged."));

		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock).converse(captor.capture());
		ConverseRequest request = captor.getValue();
		assertThat(request.modelId()).isEqualTo("test-model");
		assertThat(request.system().getFirst().text()).isEqualTo("system prompt");
		assertThat(request.messages().getFirst().content().getFirst().text()).isEqualTo("user prompt");
		assertThat(request.outputConfig().textFormat().type()).isEqualTo(OutputFormatType.JSON_SCHEMA);
		assertThat(request.outputConfig().textFormat().structure().jsonSchema().schema())
			.isEqualTo(TicketAssistService.SCHEMA);
	}

	@Test
	void skipsReasoningBlocksAndParsesEnums() {
		ContentBlock reasoning = ContentBlock
			.fromReasoningContent(ReasoningContentBlock.builder().reasoningText(text -> text.text("Hmm...")).build());
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(response(StopReason.END_TURN, reasoning,
				ContentBlock.fromText("{\"priority\": \"HIGH\", \"category\": \"BILLING\", \"reason\": \"Deadline.\"}")));

		TriageAnswer answer = client.ask("test", "s", "u", "ticket_triage", TicketTriageService.SCHEMA,
				TriageAnswer.class);

		assertThat(answer).isEqualTo(new TriageAnswer(Priority.HIGH, Category.BILLING, "Deadline."));
	}

	@Test
	void truncatedAnswerIsServiceUnavailable() {
		given(bedrock.converse(any(ConverseRequest.class)))
			.willReturn(response(StopReason.MAX_TOKENS, ContentBlock.fromText("{\"summary\": \"Printer")));

		assertThatThrownBy(this::askForAssist).isInstanceOf(ServiceUnavailableException.class);
	}

	@Test
	void answerThatIsNotJsonIsServiceUnavailable() {
		given(bedrock.converse(any(ConverseRequest.class)))
			.willReturn(response(StopReason.END_TURN, "Sure! Here is a summary..."));

		assertThatThrownBy(this::askForAssist).isInstanceOf(ServiceUnavailableException.class)
			.hasMessageContaining("JSON");
	}

	@Test
	void unknownEnumValueIsServiceUnavailable() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(response(StopReason.END_TURN,
				"{\"priority\": \"CRITICAL\", \"category\": \"BUG\", \"reason\": \"Bad.\"}"));

		assertThatThrownBy(() -> client.ask("test", "s", "u", "ticket_triage", TicketTriageService.SCHEMA,
				TriageAnswer.class))
			.isInstanceOf(ServiceUnavailableException.class);
	}

	@Test
	void missingFieldIsServiceUnavailable() {
		given(bedrock.converse(any(ConverseRequest.class)))
			.willReturn(response(StopReason.END_TURN, "{\"summary\": \"Printer smoking.\"}"));

		assertThatThrownBy(this::askForAssist).isInstanceOf(ServiceUnavailableException.class)
			.hasMessageContaining("suggestedReply");
	}

	@Test
	void bedrockErrorIsServiceUnavailable() {
		given(bedrock.converse(any(ConverseRequest.class)))
			.willThrow(SdkClientException.create("Unable to load credentials"));

		assertThatThrownBy(this::askForAssist).isInstanceOf(ServiceUnavailableException.class)
			.hasMessageContaining("unavailable");
	}

	private static ConverseResponse response(StopReason stopReason, String text) {
		return response(stopReason, ContentBlock.fromText(text));
	}

	private static ConverseResponse response(StopReason stopReason, ContentBlock... content) {
		return ConverseResponse.builder()
			.stopReason(stopReason)
			.usage(usage -> usage.inputTokens(120).outputTokens(40).totalTokens(160))
			.output(ConverseOutput
				.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).content(content).build()))
			.build();
	}

}
