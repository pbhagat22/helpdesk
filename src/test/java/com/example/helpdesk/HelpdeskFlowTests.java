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
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class HelpdeskFlowTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void ticketLifecycle() throws Exception {
		RequestPostProcessor carol = httpBasic("flow-customer@example.com", "carol-password");
		RequestPostProcessor dave = httpBasic("flow-agent@example.com", "dave-password");

		createAndGetId("/api/auth/register", null, """
				{"email": "flow-customer@example.com", "name": "Carol", "password": "carol-password"}
				""");
		// Creating an agent needs a logged-in user (an admin, once roles are enforced).
		long agentId = createAndGetId("/api/users", carol, """
				{"email": "flow-agent@example.com", "name": "Dave", "role": "AGENT", "password": "dave-password"}
				""");
		long ticketId = createAndGetId("/api/tickets", carol, """
				{"title": "Printer on fire", "description": "Smoke everywhere", "priority": "URGENT"}
				""");

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

		assertThat(mvc.get().uri("/api/tickets/{id}", ticketId).with(carol))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.reporter.name")
			.isEqualTo("Carol");

		assertThat(mvc.get().uri("/api/tickets/{id}/comments", ticketId).with(carol))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[0].author.name")
			.isEqualTo("Dave");
	}

	@Test
	void wrongPasswordIsRejected() {
		assertThat(mvc.get().uri("/api/users/me").with(httpBasic("nobody@example.com", "wrong-password")))
			.hasStatus(HttpStatus.UNAUTHORIZED);
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
