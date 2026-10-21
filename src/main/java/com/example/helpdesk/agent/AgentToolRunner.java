package com.example.helpdesk.agent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.common.ServiceUnavailableException;
import com.example.helpdesk.security.HelpdeskUserDetails;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Runs AgentTool beans by name, for every caller that lets a model use them: our own agent
// (AgentService, over Bedrock) and the MCP server (mcp package, for outside clients). Both get
// the same input checks and the same error messages.
@Component
public class AgentToolRunner {

	private static final Logger log = LoggerFactory.getLogger(AgentToolRunner.class);

	// What a call produced: the tool's output as JSON, or (if it failed) an error message
	// meant for the model. Exactly one of the two is set.
	public record Result(String output, String error) {

		public boolean failed() {
			return error != null;
		}

	}

	private final JsonMapper jsonMapper;

	private final Map<String, AgentTool<?>> tools;

	// Spring injects every AgentTool bean in the application: adding a tool = adding a @Component
	public AgentToolRunner(JsonMapper jsonMapper, List<AgentTool<?>> tools) {
		this.jsonMapper = jsonMapper;
		this.tools = tools.stream().collect(Collectors.toMap(AgentTool::name, Function.identity()));
	}

	public Collection<AgentTool<?>> tools() {
		return tools.values();
	}

	// rawInput is the model's input as plain Java values (a Map from JSON). A failing tool
	// doesn't throw: the model gets the error as the result and can try something else.
	public Result run(String name, Object rawInput, HelpdeskUserDetails me) {
		try {
			AgentTool<?> tool = tools.get(name);
			if (tool == null) {
				throw new IllegalArgumentException("There is no tool called " + name);
			}
			return new Result(jsonMapper.writeValueAsString(invoke(tool, rawInput, me)), null);
		}
		catch (RuntimeException ex) {
			return new Result(null, errorMessage(ex));
		}
	}

	// Converts the model's input (a Map) into the tool's input record; Jackson rejects
	// wrong types and unknown enum values, which become an error result.
	private <I> Object invoke(AgentTool<I> tool, Object rawInput, HelpdeskUserDetails me) {
		I input;
		try {
			input = jsonMapper.convertValue(rawInput != null ? rawInput : Map.of(), tool.inputType());
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException("Invalid input for " + tool.name() + ": " + ex.getOriginalMessage());
		}
		return tool.run(input, me);
	}

	// Our own exceptions have messages meant for users; anything else is a bug, so log it and
	// tell the model only that it failed.
	private static String errorMessage(RuntimeException ex) {
		if (ex instanceof NotFoundException || ex instanceof BadRequestException
				|| ex instanceof ServiceUnavailableException || ex instanceof IllegalArgumentException) {
			return ex.getMessage();
		}
		log.warn("Agent tool failed", ex);
		return "The tool failed because of an internal error";
	}

}
