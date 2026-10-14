package com.example.helpdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;
import com.example.helpdesk.user.UserRepository;

// A plain unit test: no Spring context, the repositories are Mockito mocks.
// Runs in milliseconds and tests only the service's own logic.
@ExtendWith(MockitoExtension.class)
class TicketServiceTests {

	@Mock
	private TicketRepository tickets;

	@Mock
	private CommentRepository comments;

	@Mock
	private UserRepository users;

	@InjectMocks
	private TicketService ticketService;

	private User customer;

	private Ticket ticket;

	@BeforeEach
	void setUp() {
		customer = new User("alice@example.com", "Alice", Role.CUSTOMER);
		ticket = new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer);
	}

	@Test
	void updateChangesOnlyTheFieldsThatAreSet() {
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));

		TicketResponse response = ticketService.update(1L, new UpdateTicketRequest(TicketStatus.RESOLVED, null, null));

		assertThat(response.status()).isEqualTo(TicketStatus.RESOLVED);
		assertThat(response.priority()).isEqualTo(Priority.HIGH);
		assertThat(response.assignee()).isNull();
	}

	@Test
	void updateAssignsTicketToAgent() {
		User agent = new User("bob@example.com", "Bob", Role.AGENT);
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));
		given(users.findById(2L)).willReturn(Optional.of(agent));

		TicketResponse response = ticketService.update(1L, new UpdateTicketRequest(null, null, 2L));

		assertThat(response.assignee().email()).isEqualTo("bob@example.com");
	}

	@Test
	void updateRejectsAssigningToCustomer() {
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));
		given(users.findById(1L)).willReturn(Optional.of(customer));

		assertThatThrownBy(() -> ticketService.update(1L, new UpdateTicketRequest(null, null, 1L)))
			.isInstanceOf(BadRequestException.class);
		assertThat(ticket.getAssignee()).isNull();
	}

	@Test
	void addCommentFailsForMissingTicket() {
		given(tickets.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> ticketService.addComment(99L, new CreateCommentRequest("Hello", 1L)))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Ticket 99 not found");
	}

}
