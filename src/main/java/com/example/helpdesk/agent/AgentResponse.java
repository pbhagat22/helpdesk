package com.example.helpdesk.agent;

import java.util.List;

// The agent's answer, plus how it got there: each tool call it made, and what the model
// calls cost in tokens.
public record AgentResponse(String answer, List<Step> steps, int modelCalls, int inputTokens, int outputTokens) {

	// One tool call: the tool, the input the model chose (as JSON), and the error if it failed
	public record Step(String tool, String input, String error) {
	}

}
