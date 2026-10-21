package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;
import com.example.helpdesk.ticket.TicketResponse;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.ticket.TicketStatus;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;

class TicketTriageServiceTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	private final BedrockJsonClient llm = mock(BedrockJsonClient.class);

	private final TicketService ticketService = mock(TicketService.class);

	private final TicketTriageRepository triages = mock(TicketTriageRepository.class);

	private final TicketTriageService service = new TicketTriageService(llm,
			new AiProperties("us-east-2", "test-model", Duration.ofSeconds(5)), ticketService, triages);

	private final TicketResponse ticket = new TicketResponse(1L, "Invoice page blank",
			"I need <b>last month's</b> invoice by Friday", TicketStatus.OPEN, Priority.URGENT, null,
			new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER, Instant.now()), null, Instant.now(),
			Instant.now());

	@Test
	void schemaAllowsExactlyTheEnumValues() {
		assertThat(TicketTriageService.SCHEMA).contains("\"enum\": [\"LOW\", \"MEDIUM\", \"HIGH\", \"URGENT\"]")
			.contains("\"enum\": [\"ACCOUNT\", \"BILLING\", \"BUG\", \"HOW_TO\", \"OTHER\"]");
	}

	@Test
	void promptEscapesTicketTextAndIncludesReporterPriority() {
		assertThat(TicketTriageService.prompt(ticket)).contains("reporter_priority=\"URGENT\"")
			.contains("I need &lt;b&gt;last month's&lt;/b&gt; invoice by Friday");
	}

	@Test
	void triageStoresTheModelsSuggestion() {
		given(ticketService.find(1L)).willReturn(Optional.of(ticket));
		given(llm.ask(eq("triage ticket 1"), eq(TicketTriageService.SYSTEM_PROMPT), contains("Invoice page blank"),
				eq("ticket_triage"), eq(TicketTriageService.SCHEMA), eq(TriageAnswer.class)))
			.willReturn(new TriageAnswer(Priority.HIGH, Category.BILLING, "Invoice needed by a deadline."));

		service.triage(1L);

		ArgumentCaptor<TicketTriage> saved = ArgumentCaptor.forClass(TicketTriage.class);
		then(triages).should().save(saved.capture());
		assertThat(saved.getValue().getTicketId()).isEqualTo(1L);
		assertThat(saved.getValue().getPriority()).isEqualTo(Priority.HIGH);
		assertThat(saved.getValue().getCategory()).isEqualTo(Category.BILLING);
		assertThat(saved.getValue().getModel()).isEqualTo("test-model");
	}

	@Test
	void longReasonIsShortenedToFitTheColumn() {
		given(ticketService.find(1L)).willReturn(Optional.of(ticket));
		given(llm.ask(anyString(), anyString(), anyString(), anyString(), anyString(), eq(TriageAnswer.class)))
			.willReturn(new TriageAnswer(Priority.LOW, Category.OTHER, "x".repeat(800)));

		service.triage(1L);

		ArgumentCaptor<TicketTriage> saved = ArgumentCaptor.forClass(TicketTriage.class);
		then(triages).should().save(saved.capture());
		assertThat(saved.getValue().getReason()).hasSize(500).endsWith("…");
	}

	// Redelivered or replayed events must not call (and pay for) the model again.
	@Test
	void alreadyTriagedTicketIsSkipped() {
		given(triages.existsByTicketId(1L)).willReturn(true);

		service.triage(1L);

		verifyNoInteractions(llm);
		then(triages).should().existsByTicketId(1L);
		then(triages).shouldHaveNoMoreInteractions();
	}

	@Test
	void deletedTicketIsSkipped() {
		given(ticketService.find(1L)).willReturn(Optional.empty());

		service.triage(1L);

		verifyNoInteractions(llm);
		then(triages).should().existsByTicketId(1L);
		then(triages).shouldHaveNoMoreInteractions();
	}

	@Test
	void getIs404UntilThereIsASuggestion() {
		given(ticketService.get(1L, bob)).willReturn(ticket);
		given(triages.findByTicketId(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(1L, bob)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void getChecksTheTicketIsVisible() {
		given(ticketService.get(any(), any())).willThrow(new NotFoundException("Ticket", 9L));

		assertThatThrownBy(() -> service.get(9L, bob)).isInstanceOf(NotFoundException.class);
		verifyNoInteractions(triages);
	}

}
