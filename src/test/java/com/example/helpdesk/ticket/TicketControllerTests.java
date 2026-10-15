package com.example.helpdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.security.SecurityConfig;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;

@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerTests {

	private static final HelpdeskUserDetails alice = new HelpdeskUserDetails(1L, "alice@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private TicketService ticketService;

	private final UserResponse aliceResponse = new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER,
			Instant.now());

	private TicketResponse ticket(TicketStatus status) {
		return new TicketResponse(1L, "Can't log in", "Password reset fails", status, Priority.HIGH, aliceResponse,
				null, Instant.now(), Instant.now());
	}

	@Test
	void createUsesLoggedInUserAsReporter() {
		// eq(1L): the controller must pass the logged-in user's id, not something from the body.
		given(ticketService.create(any(), eq(1L))).willReturn(ticket(TicketStatus.OPEN));

		assertThat(mvc.post().uri("/api/tickets").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Can't log in", "description": "Password reset fails", "priority": "HIGH"}
				"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("OPEN");
	}

	@Test
	void createRequiresLogin() {
		assertThat(mvc.post().uri("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Can't log in", "description": "Password reset fails", "priority": "HIGH"}
				"""))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.hasHeader("WWW-Authenticate", "Bearer")
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Authentication required");
		verifyNoInteractions(ticketService);
	}

	@Test
	void createReturns400WhenRequiredFieldsAreMissing() {
		assertThat(mvc.post().uri("/api/tickets").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asMap()
			.containsOnlyKeys("title", "description", "priority");
	}

	@Test
	void listPassesFiltersToService() {
		given(ticketService.list(eq(TicketStatus.OPEN), eq(2L), any())).willReturn(List.of(ticket(TicketStatus.OPEN)));

		assertThat(mvc.get().uri("/api/tickets?status=OPEN&assigneeId=2").with(user(alice)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(1);
	}

	@Test
	void listReturns400ForUnknownStatus() {
		assertThat(mvc.get().uri("/api/tickets?status=FOO").with(user(alice))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void getReturns404ForMissingTicket() {
		given(ticketService.get(eq(99L), any())).willThrow(new NotFoundException("Ticket", 99L));

		assertThat(mvc.get().uri("/api/tickets/99").with(user(alice)))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Ticket 99 not found");
	}

	@Test
	void updateReturns400WhenBusinessRuleIsBroken() {
		given(ticketService.update(eq(1L), any(), any()))
			.willThrow(new BadRequestException("Tickets can only be assigned to agents or admins"));

		assertThat(mvc.patch().uri("/api/tickets/1").with(user(bob)).contentType(MediaType.APPLICATION_JSON).content("""
				{"assigneeId": 1}
				"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Tickets can only be assigned to agents or admins");
	}

	@Test
	void updateIsForbiddenForCustomers() {
		assertThat(mvc.patch().uri("/api/tickets/1").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("""
				{"status": "CLOSED"}
				"""))
			.hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("You don't have permission to do this");
		// @PreAuthorize stops the call before the controller method (and the service) runs.
		verifyNoInteractions(ticketService);
	}

	@Test
	void updateIsAllowedForAgents() {
		given(ticketService.update(eq(1L), any(), any())).willReturn(ticket(TicketStatus.CLOSED));

		assertThat(mvc.patch().uri("/api/tickets/1").with(user(bob)).contentType(MediaType.APPLICATION_JSON).content("""
				{"status": "CLOSED"}
				"""))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("CLOSED");
	}

	@Test
	void addCommentUsesLoggedInUserAsAuthor() {
		given(ticketService.addComment(eq(1L), any(), any()))
			.willReturn(new CommentResponse(5L, 1L, aliceResponse, "Still broken", Instant.now()));

		assertThat(mvc.post().uri("/api/tickets/1/comments").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("""
				{"body": "Still broken"}
				"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.author.email")
			.isEqualTo("alice@example.com");
	}

}
