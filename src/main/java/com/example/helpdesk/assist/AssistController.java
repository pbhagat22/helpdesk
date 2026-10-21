package com.example.helpdesk.assist;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

@RestController
public class AssistController {

	private final TicketAssistService assistService;

	public AssistController(TicketAssistService assistService) {
		this.assistService = assistService;
	}

	// POST, not GET: every call costs money and gives a different answer, so it shouldn't
	// look safe to repeat or cache. Staff only: customers don't get AI drafts of replies to themselves.
	@PostMapping("/api/tickets/{id}/assist")
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public AssistResponse assist(@PathVariable Long id, @AuthenticationPrincipal HelpdeskUserDetails me) {
		return assistService.suggest(id, me);
	}

}
