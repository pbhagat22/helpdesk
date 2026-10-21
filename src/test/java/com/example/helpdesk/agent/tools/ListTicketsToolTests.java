package com.example.helpdesk.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.ticket.TicketStatus;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;

class ListTicketsToolTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	private final UserResponse alice = new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER,
			Instant.now());

	private final UserResponse carol = new UserResponse(3L, "carol@example.com", "Carol", Role.CUSTOMER,
			Instant.now());

	private final UserResponse dave = new UserResponse(4L, "dave@example.com", "Dave", Role.AGENT, Instant.now());

	private final TicketService ticketService = mock(TicketService.class);

	private final ListTicketsTool tool = new ListTicketsTool(ticketService);

	private TicketResponse ticket(long id, Priority priority, UserResponse reporter, UserResponse assignee) {
		return new TicketResponse(id, "Ticket " + id, "", TicketStatus.OPEN, priority, null, reporter, assignee,
				Instant.now(), Instant.now());
	}

	@Test
	void filtersByPriorityUnassignedAndReporter() {
		given(ticketService.list(TicketStatus.OPEN, null, null, bob)).willReturn(List.of(
				ticket(1, Priority.URGENT, alice, null), ticket(2, Priority.URGENT, alice, dave),
				ticket(3, Priority.LOW, alice, null), ticket(4, Priority.URGENT, carol, null)));

		ListTicketsTool.Result result = tool
			.run(new ListTicketsTool.Input(TicketStatus.OPEN, Priority.URGENT, null, null, true, 1L), bob);

		assertThat(result.tickets()).extracting(TicketSummary::id).containsExactly(1L);
		assertThat(result.tickets().getFirst().reporter()).isEqualTo("Alice");
	}

	@Test
	void returnsTheTotalButOnlyTheFirstTwenty() {
		given(ticketService.list(null, null, null, bob))
			.willReturn(IntStream.rangeClosed(1, 25).mapToObj(id -> ticket(id, Priority.LOW, alice, null)).toList());

		ListTicketsTool.Result result = tool.run(new ListTicketsTool.Input(null, null, null, null, null, null), bob);

		assertThat(result.total()).isEqualTo(25);
		assertThat(result.tickets()).hasSize(ListTicketsTool.LIMIT);
	}

	@Test
	void schemaListsTheEnumValues() {
		assertThat(tool.inputSchema()).contains("\"URGENT\"", "\"HOW_TO\"", "\"IN_PROGRESS\"");
	}

}
