package com.example.helpdesk.agent.tools;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.helpdesk.agent.AgentTool;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserService;

@Component
class FindUsersTool implements AgentTool<FindUsersTool.Input> {

	static final int LIMIT = 20;

	record Input(String name, Role role) {
	}

	record UserInfo(Long id, String name, String email, Role role) {
	}

	private final UserService userService;

	FindUsersTool(UserService userService) {
		this.userService = userService;
	}

	@Override
	public String name() {
		return "find_users";
	}

	@Override
	public String description() {
		return "Find customers and staff by (part of) their name or email, and/or by role. Use it to get a "
				+ "user's id, e.g. to list the tickets a customer reported or an agent is assigned to.";
	}

	@Override
	public String inputSchema() {
		return """
				{
				  "type": "object",
				  "properties": {
				    "name": {"type": "string", "description": "Part of the name or email, any case"},
				    "role": {"type": "string", "enum": [%s]}
				  }
				}""".formatted(ToolSchemas.enumValues(Role.values()));
	}

	@Override
	public Class<Input> inputType() {
		return Input.class;
	}

	// The agent endpoint is staff only, and staff may list users anyway (GET /api/users).
	@Override
	public List<UserInfo> run(Input input, HelpdeskUserDetails me) {
		String needle = input.name() != null ? input.name().toLowerCase(Locale.ROOT) : "";
		return userService.list()
			.stream()
			.filter(user -> input.role() == null || user.role() == input.role())
			.filter(user -> user.name().toLowerCase(Locale.ROOT).contains(needle)
					|| user.email().toLowerCase(Locale.ROOT).contains(needle))
			.limit(LIMIT)
			.map(user -> new UserInfo(user.id(), user.name(), user.email(), user.role()))
			.toList();
	}

}
