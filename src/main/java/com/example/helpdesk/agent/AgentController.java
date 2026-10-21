package com.example.helpdesk.agent;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

import jakarta.validation.Valid;

@RestController
public class AgentController {

	private final AgentService agentService;

	public AgentController(AgentService agentService) {
		this.agentService = agentService;
	}

	// Staff only: the tools could list other customers' tickets and the users.
	@PostMapping("/api/agent/ask")
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public AgentResponse ask(@Valid @RequestBody AskRequest request, @AuthenticationPrincipal HelpdeskUserDetails me) {
		return agentService.ask(request.question(), me);
	}

}
