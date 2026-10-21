package com.example.helpdesk.mcp;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.function.ServerRequest;

import com.example.helpdesk.agent.AgentTool;
import com.example.helpdesk.agent.AgentToolRunner;
import com.example.helpdesk.security.HelpdeskUserDetails;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// The MCP server: our AgentTool beans, offered to any MCP client (Claude Code, MCP Inspector,
// another app's agent). Spring AI's auto-configuration creates the endpoint (POST /api/mcp, see
// application.properties) and answers the protocol's requests: "initialize", "tools/list"
// (name, description and input schema of each tool below) and "tools/call", which runs one.
//
// Tools run as the user whose bearer token came with the request, exactly like in our own
// agent: same AgentToolRunner, same services, same visibility rules.
@Configuration
class McpServerConfig {

	private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

	private static final String USER = "helpdesk.user";

	// Replaces Spring AI's own transport bean (it backs off when one exists) to add a context
	// extractor: it takes the logged-in user from the HTTP request and puts it into the
	// McpTransportContext, which the SDK passes to every tool handler. That's the SDK's way of
	// handing request data to handlers. Reading SecurityContextHolder in the handler would only
	// work by luck: the SDK normally runs handlers on a Reactor worker thread (where it's empty),
	// and they're on the request thread here only because Spring AI turns on
	// immediateExecution for servlet apps.
	@Bean
	WebMvcStatelessServerTransport webMvcStatelessServerTransport(
			@Qualifier("mcpServerJsonMapper") JsonMapper mcpJsonMapper, McpServerStreamableHttpProperties properties) {
		return WebMvcStatelessServerTransport.builder()
			.jsonMapper(new JacksonMcpJsonMapper(mcpJsonMapper))
			.messageEndpoint(properties.getMcpEndpoint())
			.contextExtractor(McpServerConfig::userContext)
			.build();
	}

	private static McpTransportContext userContext(ServerRequest request) {
		if (request.principal().orElse(null) instanceof Authentication authentication
				&& authentication.getPrincipal() instanceof HelpdeskUserDetails user) {
			return McpTransportContext.create(Map.of(USER, user));
		}
		return McpTransportContext.EMPTY;
	}

	// One MCP tool per AgentTool. Spring AI picks up this list and registers it with the server.
	@Bean
	List<SyncToolSpecification> helpdeskTools(AgentToolRunner toolRunner, JsonMapper jsonMapper) {
		return toolRunner.tools().stream().map(tool -> toSpecification(tool, toolRunner, jsonMapper)).toList();
	}

	private static SyncToolSpecification toSpecification(AgentTool<?> tool, AgentToolRunner toolRunner,
			JsonMapper jsonMapper) {
		return SyncToolSpecification.builder()
			.tool(Tool
				.builder(tool.name(), jsonMapper.readValue(tool.inputSchema(), new TypeReference<Map<String, Object>>() {
				}))
				.description(tool.description())
				// Hints for the client, e.g. whether to ask before running a tool: ours only read
				.annotations(ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false).build())
				.build())
			.callHandler((context, request) -> call(toolRunner, context, request))
			.build();
	}

	// A failing tool is a normal result with isError = true (not a protocol error), so the
	// client's model sees the message and can try something else, as in our own agent.
	private static CallToolResult call(AgentToolRunner toolRunner, McpTransportContext context,
			CallToolRequest request) {
		if (!(context.get(USER) instanceof HelpdeskUserDetails me)) {
			// SecurityConfig lets only logged-in staff reach /api/mcp, so this is a bug
			throw new IllegalStateException("MCP request without an authenticated user");
		}
		AgentToolRunner.Result result = toolRunner.run(request.name(), request.arguments(), me);
		log.info("MCP tool {} for user {}: {}", request.name(), me.getId(), result.failed() ? "failed" : "ok");
		if (result.failed()) {
			return CallToolResult.builder().addTextContent(result.error()).isError(true).build();
		}
		return CallToolResult.builder().addTextContent(result.output()).build();
	}

}
