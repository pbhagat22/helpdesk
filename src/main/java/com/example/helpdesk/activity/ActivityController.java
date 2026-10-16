package com.example.helpdesk.activity;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

@RestController
public class ActivityController {

	private final TicketActivityService activityService;

	public ActivityController(TicketActivityService activityService) {
		this.activityService = activityService;
	}

	// Built asynchronously from Kafka events, so a change can take a moment to appear here
	// ("eventually consistent"), unlike the ticket itself, which is updated immediately.
	@GetMapping("/api/tickets/{ticketId}/activity")
	public List<ActivityResponse> list(@PathVariable Long ticketId, @AuthenticationPrincipal HelpdeskUserDetails me) {
		return activityService.list(ticketId, me);
	}

}
