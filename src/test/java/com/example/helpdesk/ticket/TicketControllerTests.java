package com.example.helpdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;

@WebMvcTest(TicketController.class)
class TicketControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private TicketService ticketService;

	private final UserResponse alice = new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER,
			Instant.now());

	private TicketResponse ticket(TicketStatus status) {
		return new TicketResponse(1L, "Can't log in", "Password reset fails", status, Priority.HIGH, alice, null,
				Instant.now(), Instant.now());
	}

	@Test
	void createReturns201() {
		given(ticketService.create(any())).willReturn(ticket(TicketStatus.OPEN));

		assertThat(mvc.post()
			.uri("/api/tickets")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title": "Can't log in", "description": "Password reset fails", "priority": "HIGH", "reporterId": 1}
					"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("OPEN");
	}

	@Test
	void createReturns400WhenRequiredFieldsAreMissing() {
		assertThat(mvc.post().uri("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asMap()
			.containsOnlyKeys("title", "description", "priority", "reporterId");
	}

	@Test
	void listPassesFiltersToService() {
		given(ticketService.list(TicketStatus.OPEN, 2L)).willReturn(List.of(ticket(TicketStatus.OPEN)));

		assertThat(mvc.get().uri("/api/tickets?status=OPEN&assigneeId=2"))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(1);
	}

	@Test
	void listReturns400ForUnknownStatus() {
		assertThat(mvc.get().uri("/api/tickets?status=FOO")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void getReturns404ForMissingTicket() {
		given(ticketService.get(99L)).willThrow(new NotFoundException("Ticket", 99L));

		assertThat(mvc.get().uri("/api/tickets/99"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Ticket 99 not found");
	}

	@Test
	void updateReturns400WhenBusinessRuleIsBroken() {
		given(ticketService.update(eq(1L), any()))
			.willThrow(new BadRequestException("Tickets can only be assigned to agents or admins"));

		assertThat(mvc.patch().uri("/api/tickets/1").contentType(MediaType.APPLICATION_JSON).content("""
				{"assigneeId": 1}
				"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Tickets can only be assigned to agents or admins");
	}

}
