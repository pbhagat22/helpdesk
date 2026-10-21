package com.example.helpdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;
import com.example.helpdesk.user.UserRepository;

// A plain unit test: no Spring context, the repositories are Mockito mocks.
// Runs in milliseconds and tests only the service's own logic.
@ExtendWith(MockitoExtension.class)
class TicketServiceTests {

	// The logged-in users, as Spring Security would pass them in
	private static final HelpdeskUserDetails ALICE = new HelpdeskUserDetails(1L, "alice@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails CAROL = new HelpdeskUserDetails(3L, "carol@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails BOB = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	@Mock
	private TicketRepository tickets;

	@Mock
	private CommentRepository comments;

	@Mock
	private UserRepository users;

	@Mock
	private ApplicationEventPublisher events;

	// Captures the event passed to publishEvent so the test can inspect it.
	@Captor
	private ArgumentCaptor<TicketEvent> eventCaptor;

	@InjectMocks
	private TicketService ticketService;

	private User customer;

	private Ticket ticket;

	@BeforeEach
	void setUp() {
		customer = new User("alice@example.com", "Alice", Role.CUSTOMER, "{noop}unused");
		// Entities only get ids from the database; set one directly for the ownership checks.
		ReflectionTestUtils.setField(customer, "id", 1L);
		ticket = new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer);
	}

	@Test
	void customersOnlyListTheirOwnTickets() {
		given(tickets.search(TicketStatus.OPEN, null, 1L, null)).willReturn(List.of(ticket));

		assertThat(ticketService.list(TicketStatus.OPEN, null, null, ALICE)).hasSize(1);
		then(tickets).should().search(TicketStatus.OPEN, null, 1L, null);
	}

	@Test
	void agentsListAllTickets() {
		given(tickets.search(null, null, null, Category.BILLING)).willReturn(List.of(ticket));

		assertThat(ticketService.list(null, null, Category.BILLING, BOB)).hasSize(1);
	}

	@Test
	void customerCanSeeOwnTicket() {
		given(tickets.findById(10L)).willReturn(Optional.of(ticket));

		assertThat(ticketService.get(10L, ALICE).title()).isEqualTo("Can't log in");
	}

	@Test
	void customerGets404ForSomeoneElsesTicket() {
		given(tickets.findById(10L)).willReturn(Optional.of(ticket));

		assertThatThrownBy(() -> ticketService.get(10L, CAROL)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> ticketService.addComment(10L, new CreateCommentRequest("Hi"), CAROL))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void agentCanSeeAnyTicket() {
		given(tickets.findById(10L)).willReturn(Optional.of(ticket));

		assertThat(ticketService.get(10L, BOB).title()).isEqualTo("Can't log in");
	}

	@Test
	void updateChangesOnlyTheFieldsThatAreSet() {
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));

		TicketResponse response = ticketService.update(1L, new UpdateTicketRequest(TicketStatus.RESOLVED, null, null, null),
				BOB);

		assertThat(response.status()).isEqualTo(TicketStatus.RESOLVED);
		assertThat(response.priority()).isEqualTo(Priority.HIGH);
		assertThat(response.assignee()).isNull();
	}

	@Test
	void updatePublishesOneEventPerRealChange() {
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));

		// Status changes (OPEN → RESOLVED); priority is set to the value it already has (HIGH).
		ticketService.update(1L, new UpdateTicketRequest(TicketStatus.RESOLVED, Priority.HIGH, null, null), BOB);

		then(events).should().publishEvent(eventCaptor.capture());
		then(events).shouldHaveNoMoreInteractions();
		TicketEvent event = eventCaptor.getValue();
		assertThat(event.type()).isEqualTo(TicketEventType.STATUS_CHANGED);
		assertThat(event.actorId()).isEqualTo(BOB.getId());
		assertThat(event.details()).containsEntry("from", "OPEN").containsEntry("to", "RESOLVED");
	}

	@Test
	void firstCategoryIsAChangeFromNone() {
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));

		TicketResponse response = ticketService.update(1L, new UpdateTicketRequest(null, null, null, Category.ACCOUNT),
				BOB);

		assertThat(response.category()).isEqualTo(Category.ACCOUNT);
		then(events).should().publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().type()).isEqualTo(TicketEventType.CATEGORY_CHANGED);
		assertThat(eventCaptor.getValue().details()).containsEntry("from", "NONE").containsEntry("to", "ACCOUNT");
	}

	@Test
	void updateAssignsTicketToAgent() {
		User agent = new User("bob@example.com", "Bob", Role.AGENT, "{noop}unused");
		ReflectionTestUtils.setField(agent, "id", 2L);
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));
		given(users.findById(2L)).willReturn(Optional.of(agent));

		TicketResponse response = ticketService.update(1L, new UpdateTicketRequest(null, null, 2L, null), BOB);

		assertThat(response.assignee().email()).isEqualTo("bob@example.com");
		then(events).should().publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().type()).isEqualTo(TicketEventType.ASSIGNED);
		assertThat(eventCaptor.getValue().details()).containsEntry("assigneeName", "Bob");
	}

	@Test
	void updateRejectsAssigningToCustomer() {
		given(tickets.findById(1L)).willReturn(Optional.of(ticket));
		given(users.findById(1L)).willReturn(Optional.of(customer));

		assertThatThrownBy(() -> ticketService.update(1L, new UpdateTicketRequest(null, null, 1L, null), BOB))
			.isInstanceOf(BadRequestException.class);
		assertThat(ticket.getAssignee()).isNull();
		then(events).shouldHaveNoInteractions();
	}

	@Test
	void addCommentFailsForMissingTicket() {
		given(tickets.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> ticketService.addComment(99L, new CreateCommentRequest("Hello"), ALICE))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Ticket 99 not found");
	}

}
