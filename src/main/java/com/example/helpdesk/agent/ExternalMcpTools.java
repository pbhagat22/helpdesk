package com.example.helpdesk.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

// Tools from other people's MCP servers, for our agent: this is the MCP *client* side. Spring
// AI creates one McpSyncClient per connection in spring.ai.mcp.client.*; this class asks each
// server for its tools ("tools/list") and runs them ("tools/call").
//
// External servers aren't ours, so:
// - only the tools named in helpdesk.agent.mcp-tools are offered (a server can add tools, or
//   change the descriptions the model reads, at any time);
// - a server that's down is skipped for a while instead of slowing down every question;
// - results are cut to MAX_RESULT_CHARS, because they go into the conversation that's resent
//   to the model on every call.
// Unlike our own tools, these don't run as the user: the server only sees the input the model
// wrote. The system prompt forbids customer data in those inputs (AgentService).
@Component
public class ExternalMcpTools {

	private static final Logger log = LoggerFactory.getLogger(ExternalMcpTools.class);

	static final int MAX_RESULT_CHARS = 8000;

	// How long a server's tool list is reused, and how long a failing server is skipped
	static final Duration CACHE_TIME = Duration.ofMinutes(10);

	static final Duration RETRY_AFTER = Duration.ofMinutes(1);

	// An allowed tool and the connection (spring.ai.mcp.client.*.connections.<server>) offering it
	public record ExternalTool(String server, Tool tool) {
	}

	private record Listing(List<ExternalTool> tools, Instant expiresAt) {
	}

	private final List<McpSyncClient> clients;

	private final Set<String> allowed;

	private final Clock clock;

	private final Map<McpSyncClient, Listing> listings = new ConcurrentHashMap<>();

	// No clients when spring.ai.mcp.client.enabled=false (as in the full-context tests)
	@Autowired
	public ExternalMcpTools(ObjectProvider<List<McpSyncClient>> clients, AgentProperties properties) {
		this(clients.getIfAvailable(List::of), properties, Clock.systemUTC());
	}

	ExternalMcpTools(List<McpSyncClient> clients, AgentProperties properties, Clock clock) {
		this.clients = clients;
		this.allowed = Set.copyOf(properties.mcpTools());
		this.clock = clock;
	}

	// The allowed tools of every server that's reachable right now
	public List<ExternalTool> tools() {
		if (allowed.isEmpty()) {
			return List.of();
		}
		return clients.stream().flatMap(client -> listing(client).tools().stream()).toList();
	}

	public boolean offers(String name) {
		return findClient(name) != null;
	}

	// Never throws: a failure is a Result with an error, like our own tools
	public AgentToolRunner.Result call(String name, Object rawInput) {
		McpSyncClient client = findClient(name);
		if (client == null) {
			return new AgentToolRunner.Result(null, "There is no tool called " + name);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> arguments = rawInput instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
		try {
			CallToolResult result = client.callTool(CallToolRequest.builder(name).arguments(arguments).build());
			String text = result.content()
				.stream()
				.filter(TextContent.class::isInstance)
				.map(content -> ((TextContent) content).text())
				.collect(Collectors.joining("\n"));
			if (Boolean.TRUE.equals(result.isError())) {
				return new AgentToolRunner.Result(null, text.isBlank() ? "The tool failed" : truncate(text));
			}
			return new AgentToolRunner.Result(truncate(text), null);
		}
		catch (RuntimeException ex) {
			log.warn("External MCP tool {} on {} failed: {}", name, server(client), ex.getMessage());
			return new AgentToolRunner.Result(null, "The external tool " + name + " is unavailable right now");
		}
	}

	// Only among the tools already listed (and allowed): the model can't make us call others
	private McpSyncClient findClient(String name) {
		if (!allowed.contains(name)) {
			return null;
		}
		return clients.stream()
			.filter(client -> listing(client).tools().stream().anyMatch(tool -> tool.tool().name().equals(name)))
			.findFirst()
			.orElse(null);
	}

	private Listing listing(McpSyncClient client) {
		Instant now = clock.instant();
		Listing cached = listings.get(client);
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return cached;
		}
		Listing fresh;
		try {
			// The first call also does the MCP "initialize" handshake (initialized=false)
			List<ExternalTool> tools = client.listTools()
				.tools()
				.stream()
				.filter(tool -> allowed.contains(tool.name()))
				.map(tool -> new ExternalTool(server(client), tool))
				.toList();
			log.info("MCP server {}: {} allowed tool(s) {}", server(client), tools.size(),
					tools.stream().map(tool -> tool.tool().name()).toList());
			fresh = new Listing(tools, now.plus(CACHE_TIME));
		}
		catch (RuntimeException ex) {
			log.warn("MCP server {} is unavailable, skipping its tools for {}: {}", server(client), RETRY_AFTER,
					ex.getMessage());
			fresh = new Listing(List.of(), now.plus(RETRY_AFTER));
		}
		listings.put(client, fresh);
		return fresh;
	}

	private static String server(McpSyncClient client) {
		return client.getClientInfo().title();
	}

	private static String truncate(String text) {
		return text.length() <= MAX_RESULT_CHARS ? text
				: text.substring(0, MAX_RESULT_CHARS) + "\n[cut off after " + MAX_RESULT_CHARS + " characters]";
	}

}
