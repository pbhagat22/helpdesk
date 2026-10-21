package com.example.helpdesk.agent;

import com.example.helpdesk.security.HelpdeskUserDetails;

// A function the agent may call. The model only sees the name, the description and the JSON
// schema of the input; it decides when to call the tool and with what input, and the app runs
// it. Every tool is a Spring bean, and AgentService picks them all up.
//
// Tools run as the logged-in user (me), through the normal services, so the agent can never
// see or do more than that person could through the API.
//
// I is a record matching inputSchema; the model's JSON input is converted into it.
public interface AgentTool<I> {

	// Letters, digits and underscores; the model refers to the tool by this name
	String name();

	// Read by the model to decide when to use the tool: say what it's for and what it returns
	String description();

	// JSON schema of the input object
	String inputSchema();

	Class<I> inputType();

	// The result is sent back to the model as JSON. Keep it small: every field costs tokens.
	// Throwing (e.g. NotFoundException) is fine: the model is told the call failed and why.
	Object run(I input, HelpdeskUserDetails me);

}
