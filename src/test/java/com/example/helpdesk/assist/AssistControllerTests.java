package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.common.ServiceUnavailableException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.security.SecurityConfig;
import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.user.Role;

@WebMvcTest(AssistController.class)
@Import(SecurityConfig.class)
class AssistControllerTests {

	private static final HelpdeskUserDetails alice = new HelpdeskUserDetails(1L, "alice@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private TicketAssistService assistService;

	@MockitoBean
	private TicketTriageService triageService;

	@Test
	void agentGetsSummaryAndSuggestedReply() {
		given(assistService.suggest(1L, bob)).willReturn(new AssistResponse("Printer smoking.", "Please unplug it.",
				List.of(new AssistResponse.Source(7L, "Printer safety"))));

		var response = assertThat(mvc.post().uri("/api/tickets/1/assist").with(user(bob))).hasStatusOk().bodyJson();
		response.extractingPath("$.suggestedReply").isEqualTo("Please unplug it.");
		response.extractingPath("$.sources[0].title").isEqualTo("Printer safety");
	}

	@Test
	void customerIsForbidden() {
		assertThat(mvc.post().uri("/api/tickets/1/assist").with(user(alice))).hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(assistService);
	}

	@Test
	void bedrockDownIsProblemDetail503() {
		given(assistService.suggest(1L, bob))
			.willThrow(new ServiceUnavailableException("The AI assistant is unavailable right now", null));

		assertThat(mvc.post().uri("/api/tickets/1/assist").with(user(bob)))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("The AI assistant is unavailable right now");
	}

	@Test
	void agentGetsTheTriageSuggestion() {
		given(triageService.get(1L, bob)).willReturn(
				new TriageResponse(Priority.HIGH, Category.BILLING, "Deadline.", "test-model", Instant.now()));

		assertThat(mvc.get().uri("/api/tickets/1/triage").with(user(bob))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.category")
			.isEqualTo("BILLING");
	}

	@Test
	void triageIs404UntilThereIsOne() {
		given(triageService.get(1L, bob)).willThrow(new NotFoundException("Triage for ticket", 1L));

		assertThat(mvc.get().uri("/api/tickets/1/triage").with(user(bob))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void customerCannotSeeTriage() {
		assertThat(mvc.get().uri("/api/tickets/1/triage").with(user(alice))).hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(triageService);
	}

}
