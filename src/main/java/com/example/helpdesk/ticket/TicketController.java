package com.example.helpdesk.ticket;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse create(@Valid @RequestBody CreateTicketRequest request,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return ticketService.create(request, me.getId());
	}

	// GET /api/tickets?status=OPEN&assigneeId=2 (both filters optional)
	@GetMapping
	public List<TicketResponse> list(@RequestParam(required = false) TicketStatus status,
			@RequestParam(required = false) Long assigneeId) {
		return ticketService.list(status, assigneeId);
	}

	@GetMapping("/{id}")
	public TicketResponse get(@PathVariable Long id) {
		return ticketService.get(id);
	}

	@PatchMapping("/{id}")
	public TicketResponse update(@PathVariable Long id, @RequestBody UpdateTicketRequest request) {
		return ticketService.update(id, request);
	}

	@GetMapping("/{id}/comments")
	public List<CommentResponse> listComments(@PathVariable Long id) {
		return ticketService.listComments(id);
	}

	@PostMapping("/{id}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CreateCommentRequest request,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return ticketService.addComment(id, request, me.getId());
	}

}
