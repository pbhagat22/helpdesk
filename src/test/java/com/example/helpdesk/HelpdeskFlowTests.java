package com.example.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

// End-to-end test: the whole application (security, controllers, services, JPA, Postgres)
// with nothing mocked, walking through a typical ticket's life. Users log in for real
// with HTTP Basic, so passwords are hashed and checked as in production. Slower than
// the slice tests, so keep these few and focused on how the pieces fit together.
// The properties configure the bootstrap admin, as HELPDESK_ADMIN_* would in production.
@SpringBootTest(properties = { "helpdesk.admin.email=admin@example.com", "helpdesk.admin.password=admin-password" })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class HelpdeskFlowTests {

	private static final RequestPostProcessor ADMIN = httpBasic("admin@example.com", "admin-password");

	@Autowired
	private MockMvcTester mvc;

	@Test
	void ticketLifecycle() throws Exception {
		RequestPostProcessor carol = register("flow-carol@example.com", "Carol", "carol-password");
		RequestPostProcessor dave = httpBasic("flow-dave@example.com", "dave-password");

		// Only the admin can create agent accounts.
		long agentId = createAndGetId("/api/users", ADMIN, """
				{"email": "flow-dave@example.com", "name": "Dave", "role": "AGENT", "password": "dave-password"}
				""");
		long ticketId = createAndGetId("/api/tickets", carol, """
				{"title": "Printer on fire", "description": "Smoke everywhere", "priority": "URGENT"}
				""");

		// The agent picks the ticket up and comments on it.
		assertThat(mvc.patch()
			.uri("/api/tickets/{id}", ticketId)
			.with(dave)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "IN_PROGRESS", "assigneeId": %d}
					""".formatted(agentId)))
			.hasStatus(HttpStatus.OK);
		createAndGetId("/api/tickets/" + ticketId + "/comments", dave, """
				{"body": "Fire extinguisher deployed"}
				""");

		// The customer sees the progress on their own ticket.
		assertThat(mvc.get().uri("/api/tickets/{id}", ticketId).with(carol))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.assignee.name")
			.isEqualTo("Dave");
		assertThat(mvc.get().uri("/api/tickets/{id}/comments", ticketId).with(carol))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[0].author.name")
			.isEqualTo("Dave");
	}

	@Test
	void customersAreKeptApart() throws Exception {
		RequestPostProcessor erin = register("flow-erin@example.com", "Erin", "erin-password");
		RequestPostProcessor frank = register("flow-frank@example.com", "Frank", "frank-password");
		long erinsTicket = createAndGetId("/api/tickets", erin, """
				{"title": "Erin's problem", "description": "Private details", "priority": "LOW"}
				""");

		// Frank can't read, comment on, or even detect Erin's ticket...
		assertThat(mvc.get().uri("/api/tickets/{id}", erinsTicket).with(frank)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post()
			.uri("/api/tickets/{id}/comments", erinsTicket)
			.with(frank)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"body": "Hello?"}
					"""))
			.hasStatus(HttpStatus.NOT_FOUND);
		// ...it isn't in his list...
		assertThat(mvc.get().uri("/api/tickets").with(frank)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[*].id")
			.asArray()
			.doesNotContain((int) erinsTicket);
		// ...and customers can't do staff work or create accounts.
		assertThat(mvc.patch()
			.uri("/api/tickets/{id}", erinsTicket)
			.with(erin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "CLOSED"}
					"""))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/users").with(erin)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void wrongPasswordIsRejected() {
		assertThat(mvc.get().uri("/api/users/me").with(httpBasic("admin@example.com", "wrong-password")))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// Signs up a customer and returns their login.
	private RequestPostProcessor register(String email, String name, String password) throws Exception {
		createAndGetId("/api/auth/register", null, """
				{"email": "%s", "name": "%s", "password": "%s"}
				""".formatted(email, name, password));
		return httpBasic(email, password);
	}

	// POSTs as the given user (null = not logged in) and returns the new resource's id.
	private long createAndGetId(String uri, RequestPostProcessor user, String json) throws Exception {
		var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
		if (user != null) {
			request = request.with(user);
		}
		MvcTestResult result = request.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
	}

}
