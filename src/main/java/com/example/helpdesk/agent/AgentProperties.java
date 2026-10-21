package com.example.helpdesk.agent;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// helpdesk.agent.* (or HELPDESK_AGENT_MCP_TOOLS, comma-separated).
// - mcpTools: the tools from external MCP servers (spring.ai.mcp.client.*) that the agent may
//   use, by name. Empty (the default) means none: a server's tools are never offered just
//   because it's connected.
@ConfigurationProperties("helpdesk.agent")
public record AgentProperties(List<String> mcpTools) {

	public AgentProperties {
		mcpTools = mcpTools == null ? List.of() : List.copyOf(mcpTools);
	}

}
