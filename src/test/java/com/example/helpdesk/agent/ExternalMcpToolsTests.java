package com.example.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

class ExternalMcpToolsTests {

	// A clock the test moves forward by hand
	static class TestClock extends Clock {

		Instant now = Instant.parse("2026-10-21T12:00:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

	private final McpSyncClient client = mock(McpSyncClient.class);

	private final TestClock clock = new TestClock();

	private final ExternalMcpTools tools = new ExternalMcpTools(List.of(client),
			new AgentProperties(List.of("search_docs")), clock);

	@BeforeEach
	void setUp() {
		given(client.getClientInfo()).willReturn(Implementation.builder("helpdesk - docs", "1.0.0").title("docs").build());
		given(client.listTools()).willReturn(ListToolsResult
			.builder(List.of(tool("search_docs"), tool("read_docs"), tool("delete_everything")))
			.build());
	}

	@Test
	void offersOnlyTheAllowedTools() {
		assertThat(tools.tools()).extracting(tool -> tool.tool().name()).containsExactly("search_docs");
		assertThat(tools.tools().getFirst().server()).isEqualTo("docs");
		assertThat(tools.offers("search_docs")).isTrue();
		assertThat(tools.offers("delete_everything")).isFalse();
		// The model can't make us call a tool we didn't offer, even if the server has it
		assertThat(tools.call("delete_everything", Map.of()).error()).isEqualTo("There is no tool called delete_everything");
		then(client).should(never()).callTool(any());
	}

	@Test
	void withNothingAllowedServersAreNeverAsked() {
		ExternalMcpTools none = new ExternalMcpTools(List.of(client), new AgentProperties(null), clock);

		assertThat(none.tools()).isEmpty();
		then(client).should(never()).listTools();
	}

	@Test
	void callsTheToolAndReturnsItsText() {
		given(client.callTool(any())).willReturn(CallToolResult.builder().addTextContent("Lambda times out after 15 minutes").build());

		AgentToolRunner.Result result = tools.call("search_docs", Map.of("search_phrase", "lambda timeout"));

		assertThat(result.failed()).isFalse();
		assertThat(result.output()).isEqualTo("Lambda times out after 15 minutes");
		ArgumentCaptor<CallToolRequest> request = ArgumentCaptor.forClass(CallToolRequest.class);
		then(client).should().callTool(request.capture());
		assertThat(request.getValue().name()).isEqualTo("search_docs");
		assertThat(request.getValue().arguments()).isEqualTo(Map.of("search_phrase", "lambda timeout"));
	}

	@Test
	void toolErrorsAndBrokenConnectionsBecomeErrorResults() {
		given(client.callTool(any())).willReturn(CallToolResult.builder().addTextContent("Unknown topic").isError(true).build())
			.willThrow(new RuntimeException("Connection reset"));

		assertThat(tools.call("search_docs", Map.of()).error()).isEqualTo("Unknown topic");
		assertThat(tools.call("search_docs", Map.of()).error())
			.isEqualTo("The external tool search_docs is unavailable right now");
	}

	@Test
	void longResultsAreCutOff() {
		given(client.callTool(any())).willReturn(CallToolResult.builder().addTextContent("x".repeat(20_000)).build());

		String output = tools.call("search_docs", Map.of()).output();

		assertThat(output).startsWith("x".repeat(ExternalMcpTools.MAX_RESULT_CHARS))
			.endsWith("[cut off after " + ExternalMcpTools.MAX_RESULT_CHARS + " characters]");
	}

	@Test
	void theToolListIsCachedAndADownServerIsRetriedLater() {
		tools.tools();
		tools.tools();
		then(client).should(times(1)).listTools();

		// After the cache time the list is fetched again; this time the server is down
		clock.now = clock.now.plus(ExternalMcpTools.CACHE_TIME);
		given(client.listTools()).willThrow(new RuntimeException("Connection refused"));
		assertThat(tools.tools()).isEmpty();
		assertThat(tools.tools()).isEmpty();
		then(client).should(times(2)).listTools();

		// Skipped until RETRY_AFTER has passed, then asked again
		clock.now = clock.now.plus(ExternalMcpTools.RETRY_AFTER);
		tools.tools();
		then(client).should(times(3)).listTools();
	}

	private static Tool tool(String name) {
		return Tool.builder(name, Map.of("type", "object")).description("A tool called " + name).build();
	}

}
