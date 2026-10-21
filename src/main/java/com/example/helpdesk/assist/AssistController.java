package com.example.helpdesk.assist;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

// The AI features. Staff only: customers don't get AI drafts of replies to themselves or
// see how their tickets were rated.
@RestController
public class AssistController {

	private final TicketAssistService assistService;

	private final TicketTriageService triageService;

	public AssistController(TicketAssistService assistService, TicketTriageService triageService) {
		this.assistService = assistService;
		this.triageService = triageService;
	}

	// POST, not GET: every call costs money and gives a different answer, so it shouldn't
	// look safe to repeat or cache.
	@PostMapping("/api/tickets/{id}/assist")
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public AssistResponse assist(@PathVariable Long id, @AuthenticationPrincipal HelpdeskUserDetails me) {
		return assistService.suggest(id, me);
	}

	// The stored triage suggestion (made in the background when the ticket was created).
	// 404 until there is one.
	@GetMapping("/api/tickets/{id}/triage")
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public TriageResponse triage(@PathVariable Long id, @AuthenticationPrincipal HelpdeskUserDetails me) {
		return triageService.get(id, me);
	}

}
