package com.example.helpdesk.agent.tools;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.helpdesk.agent.AgentTool;
import com.example.helpdesk.assist.TicketTriageService;
import com.example.helpdesk.assist.TriageResponse;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.user.Role;

@Component
class GetTicketTool implements AgentTool<GetTicketTool.Input> {

	record Input(Long id) {
	}

	record CommentInfo(String author, String from, String body, Instant at) {
	}

	record TriageInfo(String priority, String category, String reason) {
	}

	// triage: the AI's suggestion when the ticket was created (null if there is none)
	record Result(TicketSummary ticket, String description, List<CommentInfo> comments, TriageInfo triage) {
	}

	private final TicketService ticketService;

	private final TicketTriageService triageService;

	GetTicketTool(TicketService ticketService, TicketTriageService triageService) {
		this.ticketService = ticketService;
		this.triageService = triageService;
	}

	@Override
	public String name() {
		return "get_ticket";
	}

	@Override
	public String description() {
		return "Get one ticket by its number: details, description, the whole conversation (comments, oldest "
				+ "first) and the AI triage suggestion if there is one.";
	}

	@Override
	public String inputSchema() {
		return """
				{
				  "type": "object",
				  "properties": {
				    "id": {"type": "integer", "description": "The ticket number"}
				  },
				  "required": ["id"]
				}""";
	}

	@Override
	public Class<Input> inputType() {
		return Input.class;
	}

	@Override
	public Result run(Input input, HelpdeskUserDetails me) {
		if (input.id() == null) {
			throw new IllegalArgumentException("id is required");
		}
		TicketResponse ticket = ticketService.get(input.id(), me);
		List<CommentInfo> comments = ticketService.listComments(input.id(), me)
			.stream()
			.map(comment -> new CommentInfo(comment.author().name(),
					comment.author().role() == Role.CUSTOMER ? "customer" : "support", comment.body(),
					comment.createdAt()))
			.toList();
		TriageInfo triage;
		try {
			TriageResponse suggestion = triageService.get(input.id(), me);
			triage = new TriageInfo(suggestion.priority().name(), suggestion.category().name(), suggestion.reason());
		}
		catch (NotFoundException ex) {
			triage = null;
		}
		return new Result(TicketSummary.from(ticket), ticket.description(), comments, triage);
	}

}
