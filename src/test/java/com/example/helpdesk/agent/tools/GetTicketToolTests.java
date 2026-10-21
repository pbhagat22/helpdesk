package com.example.helpdesk.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.helpdesk.assist.TicketTriageService;
import com.example.helpdesk.assist.TriageResponse;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.CommentResponse;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.ticket.TicketStatus;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;

class GetTicketToolTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	private final UserResponse alice = new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER,
			Instant.now());

	private final TicketService ticketService = mock(TicketService.class);

	private final TicketTriageService triageService = mock(TicketTriageService.class);

	private final GetTicketTool tool = new GetTicketTool(ticketService, triageService);

	@Test
	void returnsTicketConversationAndTriage() {
		given(ticketService.get(5L, bob)).willReturn(new TicketResponse(5L, "Printer on fire", "Smoke everywhere",
				TicketStatus.OPEN, Priority.URGENT, null, alice, null, Instant.now(), Instant.now()));
		given(ticketService.listComments(5L, bob))
			.willReturn(List.of(new CommentResponse(1L, 5L, alice, "Still smoking!", Instant.now())));
		given(triageService.get(5L, bob)).willReturn(
				new TriageResponse(Priority.URGENT, Category.BUG, "Safety hazard.", "model", Instant.now()));

		GetTicketTool.Result result = tool.run(new GetTicketTool.Input(5L), bob);

		assertThat(result.description()).isEqualTo("Smoke everywhere");
		assertThat(result.comments()).singleElement()
			.satisfies(comment -> assertThat(comment.from()).isEqualTo("customer"));
		assertThat(result.triage().reason()).isEqualTo("Safety hazard.");
	}

	@Test
	void ticketWithoutTriageHasNullTriage() {
		given(ticketService.get(5L, bob)).willReturn(new TicketResponse(5L, "Printer on fire", "Smoke everywhere",
				TicketStatus.OPEN, Priority.URGENT, null, alice, null, Instant.now(), Instant.now()));
		given(triageService.get(5L, bob)).willThrow(new NotFoundException("Triage for ticket", 5L));

		assertThat(tool.run(new GetTicketTool.Input(5L), bob).triage()).isNull();
	}

	@Test
	void unknownTicketThrowsSoTheModelIsTold() {
		given(ticketService.get(99L, bob)).willThrow(new NotFoundException("Ticket", 99L));

		assertThatThrownBy(() -> tool.run(new GetTicketTool.Input(99L), bob)).isInstanceOf(NotFoundException.class);
	}

}
