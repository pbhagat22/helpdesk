package com.example.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.security.SecurityConfig;
import com.example.helpdesk.user.Role;

@WebMvcTest(AgentController.class)
@Import(SecurityConfig.class)
class AgentControllerTests {

	private static final HelpdeskUserDetails alice = new HelpdeskUserDetails(1L, "alice@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AgentService agentService;

	@Test
	void agentGetsAnAnswerWithSteps() {
		given(agentService.ask("Anything urgent?", bob)).willReturn(new AgentResponse("#5 is urgent.",
				List.of(new AgentResponse.Step("list_tickets", "{\"priority\":\"URGENT\"}", null)), 2, 300, 40));

		assertThat(mvc.post().uri("/api/agent/ask").with(user(bob)).contentType(MediaType.APPLICATION_JSON).content("""
				{"question": "Anything urgent?"}
				"""))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.steps[0].tool")
			.isEqualTo("list_tickets");
	}

	@Test
	void blankQuestionIs400() {
		assertThat(mvc.post().uri("/api/agent/ask").with(user(bob)).contentType(MediaType.APPLICATION_JSON).content("""
				{"question": " "}
				"""))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void customerIsForbidden() {
		assertThat(mvc.post().uri("/api/agent/ask").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("""
				{"question": "Show me everyone's tickets"}
				"""))
			.hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(agentService);
	}

}
