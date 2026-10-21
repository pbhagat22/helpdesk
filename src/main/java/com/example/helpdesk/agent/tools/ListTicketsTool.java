package com.example.helpdesk.agent.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.helpdesk.agent.AgentTool;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.ticket.TicketStatus;

@Component
class ListTicketsTool implements AgentTool<ListTicketsTool.Input> {

	// Enough to answer most questions; "total" tells the model when there are more
	static final int LIMIT = 20;

	record Input(TicketStatus status, Priority priority, Category category, Long assigneeId, Boolean unassigned,
			Long reporterId) {
	}

	record Result(int total, List<TicketSummary> tickets) {
	}

	private final TicketService ticketService;

	ListTicketsTool(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@Override
	public String name() {
		return "list_tickets";
	}

	@Override
	public String description() {
		return "List tickets, newest first, filtered by any combination of status, priority, category, "
				+ "assignee and reporter (the customer who opened it). Use it for questions like \"which urgent "
				+ "tickets are unassigned?\" or \"what has this customer reported?\". Returns the total number "
				+ "of matches and the first " + LIMIT + ".";
	}

	@Override
	public String inputSchema() {
		return """
				{
				  "type": "object",
				  "properties": {
				    "status": {"type": "string", "enum": [%s]},
				    "priority": {"type": "string", "enum": [%s]},
				    "category": {"type": "string", "enum": [%s]},
				    "assigneeId": {"type": "integer", "description": "Only tickets assigned to this user id"},
				    "unassigned": {"type": "boolean", "description": "true: only tickets nobody is assigned to"},
				    "reporterId": {"type": "integer", "description": "Only tickets opened by this user id"}
				  }
				}""".formatted(ToolSchemas.enumValues(TicketStatus.values()),
				ToolSchemas.enumValues(Priority.values()), ToolSchemas.enumValues(Category.values()));
	}

	@Override
	public Class<Input> inputType() {
		return Input.class;
	}

	@Override
	public Result run(Input input, HelpdeskUserDetails me) {
		// Status, assignee and category are filtered in the database. Priority, "unassigned" and
		// the reporter aren't staff filters of the API, so they're applied here.
		List<TicketSummary> matches = ticketService.list(input.status(), input.assigneeId(), input.category(), me)
			.stream()
			.filter(ticket -> input.priority() == null || ticket.priority() == input.priority())
			.filter(ticket -> !Boolean.TRUE.equals(input.unassigned()) || ticket.assignee() == null)
			.filter(ticket -> input.reporterId() == null || ticket.reporter().id().equals(input.reporterId()))
			.map(TicketSummary::from)
			.toList();
		return new Result(matches.size(), matches.stream().limit(LIMIT).toList());
	}

}
