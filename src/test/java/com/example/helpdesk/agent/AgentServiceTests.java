package com.example.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.helpdesk.assist.AiCaller;
import com.example.helpdesk.assist.AiFeature;
import com.example.helpdesk.assist.AiProperties;
import com.example.helpdesk.assist.AiUsageService;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.common.ServiceUnavailableException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import tools.jackson.databind.json.JsonMapper;

class AgentServiceTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	// A tool for the tests: echoes the ticket id, or fails for id 99
	record EchoInput(Long id) {
	}

	static class EchoTool implements AgentTool<EchoInput> {

		HelpdeskUserDetails ranAs;

		@Override
		public String name() {
			return "get_ticket";
		}

		@Override
		public String description() {
			return "Echo";
		}

		@Override
		public String inputSchema() {
			return """
					{"type": "object", "properties": {"id": {"type": "integer"}}, "required": ["id"]}""";
		}

		@Override
		public Class<EchoInput> inputType() {
			return EchoInput.class;
		}

		@Override
		public Object run(EchoInput input, HelpdeskUserDetails me) {
			ranAs = me;
			if (input.id() == 99) {
				throw new NotFoundException("Ticket", 99L);
			}
			return Map.of("id", input.id(), "title", "Printer on fire");
		}

	}

	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);

	private final EchoTool tool = new EchoTool();

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final AiProperties properties = new AiProperties("us-east-2", "small-model", "agent-model",
			"test-embedding-model", Duration.ofSeconds(5));

	private final AiUsageService usage = mock(AiUsageService.class);

	// No external MCP servers, except in the test that's about them
	private final AgentService agent = new AgentService(bedrock, properties, jsonMapper,
			new AgentToolRunner(jsonMapper, List.of(tool)),
			new ExternalMcpTools(List.of(), new AgentProperties(null), Clock.systemUTC()), usage);

	@Test
	void runsTheRequestedToolAndSendsTheResultBack() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(
				response(StopReason.TOOL_USE, toolUse("call-1", "get_ticket", Map.of("id", 5))),
				response(StopReason.END_TURN, ContentBlock.fromText("#5 is about a printer on fire.")));

		AgentResponse result = agent.ask("What is ticket 5 about?", bob);

		assertThat(result.answer()).isEqualTo("#5 is about a printer on fire.");
		assertThat(result.steps()).containsExactly(new AgentResponse.Step("get_ticket", "{\"id\":5}", null));
		assertThat(result.modelCalls()).isEqualTo(2);
		assertThat(result.inputTokens()).isEqualTo(200);
		// Every model call is recorded in the usage ledger, against the user who asked
		verify(usage, times(2)).record(AiCaller.user(AiFeature.AGENT, bob.getId()), "agent-model", 100, 20);
		// The tool ran as the user who asked, not with any extra rights
		assertThat(tool.ranAs).isSameAs(bob);

		ArgumentCaptor<ConverseRequest> requests = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock, times(2)).converse(requests.capture());
		ConverseRequest first = requests.getAllValues().get(0);
		assertThat(first.modelId()).isEqualTo("agent-model");
		assertThat(first.toolConfig().tools()).singleElement()
			.satisfies(t -> assertThat(t.toolSpec().name()).isEqualTo("get_ticket"));
		assertThat(first.system().getFirst().text()).contains("bob@example.com", "user id 2");
		// Second call: question, the model's tool request, then our result for that request id
		List<Message> history = requests.getAllValues().get(1).messages();
		assertThat(history).hasSize(3);
		ToolResultBlock toolResult = history.get(2).content().getFirst().toolResult();
		assertThat(toolResult.toolUseId()).isEqualTo("call-1");
		assertThat(toolResult.status()).isNull();
		assertThat(toolResult.content().getFirst().text()).contains("Printer on fire");
	}

	@Test
	void parallelToolCallsAreAnsweredInOneMessage() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(
				response(StopReason.TOOL_USE, toolUse("a", "get_ticket", Map.of("id", 1)),
						toolUse("b", "get_ticket", Map.of("id", 2))),
				response(StopReason.END_TURN, ContentBlock.fromText("Done.")));

		agent.ask("Tickets 1 and 2?", bob);

		ArgumentCaptor<ConverseRequest> requests = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock, times(2)).converse(requests.capture());
		Message results = requests.getAllValues().get(1).messages().get(2);
		assertThat(results.content()).extracting(block -> block.toolResult().toolUseId()).containsExactly("a", "b");
	}

	@Test
	void toolErrorsGoBackToTheModelInsteadOfFailingTheRequest() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(
				response(StopReason.TOOL_USE, toolUse("call-1", "get_ticket", Map.of("id", 99)),
						toolUse("call-2", "delete_everything", Map.of())),
				response(StopReason.END_TURN, ContentBlock.fromText("Ticket #99 doesn't exist.")));

		AgentResponse result = agent.ask("What is ticket 99 about?", bob);

		assertThat(result.steps()).extracting(AgentResponse.Step::error)
			.containsExactly("Ticket 99 not found", "There is no tool called delete_everything");
		ArgumentCaptor<ConverseRequest> requests = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock, times(2)).converse(requests.capture());
		assertThat(requests.getAllValues().get(1).messages().get(2).content())
			.extracting(block -> block.toolResult().status())
			.containsOnly(ToolResultStatus.ERROR);
	}

	@Test
	void invalidToolInputIsAnErrorResult() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(
				response(StopReason.TOOL_USE, toolUse("call-1", "get_ticket", Map.of("id", "not a number"))),
				response(StopReason.END_TURN, ContentBlock.fromText("Sorry.")));

		AgentResponse result = agent.ask("?", bob);

		assertThat(result.steps()).singleElement().satisfies(step -> assertThat(step.error()).isNotBlank());
	}

	// Tools from an external MCP server are offered next to ours, marked as external, and
	// called on that server with only the model's input.
	@Test
	void externalMcpToolsAreOfferedMarkedAndRunOnTheirServer() {
		AgentService withDocs = agentWithDocsServer();
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(
				response(StopReason.TOOL_USE, toolUse("call-1", "search_docs", Map.of("search_phrase", "lambda timeout"))),
				response(StopReason.END_TURN, ContentBlock.fromText("At most 15 minutes.")));

		AgentResponse result = withDocs.ask("What's the longest a Lambda function can run?", bob);

		assertThat(result.steps())
			.containsExactly(new AgentResponse.Step("search_docs", "{\"search_phrase\":\"lambda timeout\"}", null));
		assertThat(tool.ranAs).isNull();
		ArgumentCaptor<ConverseRequest> requests = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock, times(2)).converse(requests.capture());
		assertThat(requests.getAllValues().get(0).toolConfig().tools()).extracting(t -> t.toolSpec().name())
			.containsExactly("get_ticket", "search_docs");
		assertThat(requests.getAllValues().get(0).toolConfig().tools().get(1).toolSpec().description())
			.isEqualTo("[External: docs] Searches the docs");
		assertThat(requests.getAllValues().get(1).messages().get(2).content().getFirst().toolResult().content().getFirst().text())
			.isEqualTo("Lambda: at most 15 minutes");
	}

	// Once our tools have put helpdesk data into the conversation, nothing may carry it to an
	// external server, whatever the model decides. Calls in the same turn are still fine: the
	// model hadn't seen any results when it made them.
	@Test
	void externalToolsAreBlockedAfterOurToolsReturnedData() {
		AgentService withDocs = agentWithDocsServer();
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(
				response(StopReason.TOOL_USE, toolUse("a", "get_ticket", Map.of("id", 5)),
						toolUse("b", "search_docs", Map.of("search_phrase", "printer"))),
				response(StopReason.TOOL_USE, toolUse("c", "search_docs", Map.of("search_phrase", "Printer on fire"))),
				response(StopReason.END_TURN, ContentBlock.fromText("Done.")));

		AgentResponse result = withDocs.ask("Look up ticket 5 in the docs", bob);

		assertThat(result.steps()).extracting(AgentResponse.Step::error)
			.containsExactly(null, null, AgentService.EXTERNAL_LOCKED);
		verify(docs, times(1)).callTool(any());
	}

	private final McpSyncClient docs = mock(McpSyncClient.class);

	// An agent that also has a "docs" MCP server offering one allowed tool, search_docs
	private AgentService agentWithDocsServer() {
		given(docs.getClientInfo()).willReturn(McpSchema.Implementation.builder("helpdesk - docs", "1.0.0").title("docs").build());
		given(docs.listTools()).willReturn(McpSchema.ListToolsResult
			.builder(List.of(McpSchema.Tool.builder("search_docs", Map.of("type", "object")).description("Searches the docs").build()))
			.build());
		given(docs.callTool(any())).willReturn(McpSchema.CallToolResult.builder().addTextContent("Lambda: at most 15 minutes").build());
		return new AgentService(bedrock, properties, jsonMapper, new AgentToolRunner(jsonMapper, List.of(tool)),
				new ExternalMcpTools(List.of(docs), new AgentProperties(List.of("search_docs")), Clock.systemUTC()), usage);
	}

	// A model that never stops calling tools must not loop (and bill) forever.
	@Test
	void stopsAfterTooManyModelCalls() {
		given(bedrock.converse(any(ConverseRequest.class)))
			.willReturn(response(StopReason.TOOL_USE, toolUse("again", "get_ticket", Map.of("id", 1))));

		assertThatThrownBy(() -> agent.ask("Loop forever", bob)).isInstanceOf(ServiceUnavailableException.class)
			.hasMessageContaining("steps");
		verify(bedrock, times(AgentService.MAX_MODEL_CALLS)).converse(any(ConverseRequest.class));
	}

	@Test
	void truncatedAnswerIsServiceUnavailable() {
		given(bedrock.converse(any(ConverseRequest.class)))
			.willReturn(response(StopReason.MAX_TOKENS, ContentBlock.fromText("The tickets are")));

		assertThatThrownBy(() -> agent.ask("?", bob)).isInstanceOf(ServiceUnavailableException.class);
	}

	private static ContentBlock toolUse(String id, String name, Map<String, Object> input) {
		return ContentBlock.fromToolUse(
				ToolUseBlock.builder().toolUseId(id).name(name).input(Documents.fromJava(input)).build());
	}

	private static ConverseResponse response(StopReason stopReason, ContentBlock... content) {
		return ConverseResponse.builder()
			.stopReason(stopReason)
			.usage(usage -> usage.inputTokens(100).outputTokens(20).totalTokens(120))
			.output(ConverseOutput
				.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).content(content).build()))
			.build();
	}

}
