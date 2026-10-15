package com.example.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
// and send the token they get back, as the React UI will. Slower than
// the slice tests, so keep these few and focused on how the pieces fit together.
// The properties configure the bootstrap admin, as HELPDESK_ADMIN_* would in production.
@SpringBootTest(properties = { "helpdesk.admin.email=admin@example.com", "helpdesk.admin.password=admin-password" })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class HelpdeskFlowTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void ticketLifecycle() throws Exception {
		RequestPostProcessor admin = login("admin@example.com", "admin-password");
		RequestPostProcessor carol = register("flow-carol@example.com", "Carol", "carol-password");

		// Only the admin can create agent accounts.
		long agentId = createAndGetId("/api/users", admin, """
				{"email": "flow-dave@example.com", "name": "Dave", "role": "AGENT", "password": "dave-password"}
				""");
		RequestPostProcessor dave = login("flow-dave@example.com", "dave-password");
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
	void tokenIdentifiesTheUser() throws Exception {
		RequestPostProcessor gina = register("flow-gina@example.com", "Gina", "gina-password");

		assertThat(mvc.get().uri("/api/users/me").with(gina))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Gina");
	}

	@Test
	void wrongPasswordGetsNoToken() {
		assertThat(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "admin@example.com", "password": "wrong-password"}
				"""))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void missingOrTamperedTokenIsRejected() throws Exception {
		assertThat(mvc.get().uri("/api/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);

		// A token is header.payload.signature. Swap in a payload claiming to be the admin
		// (user 1, role ADMIN): the signature no longer matches, so it's rejected.
		String token = loginForToken("admin@example.com", "admin-password");
		String[] parts = token.split("\\.");
		String forgedPayload = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString("""
					{"iss":"helpdesk","sub":"1","email":"evil@example.com","role":"ADMIN","exp":9999999999}
					""".strip().getBytes(StandardCharsets.UTF_8));
		String forged = parts[0] + "." + forgedPayload + "." + parts[2];

		assertThat(mvc.get().uri("/api/users/me").header("Authorization", "Bearer " + forged))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// Signs up a customer and returns their login.
	private RequestPostProcessor register(String email, String name, String password) throws Exception {
		createAndGetId("/api/auth/register", null, """
				{"email": "%s", "name": "%s", "password": "%s"}
				""".formatted(email, name, password));
		return login(email, password);
	}

	// Logs in and returns something that adds "Authorization: Bearer <token>" to a request.
	private RequestPostProcessor login(String email, String password) throws Exception {
		String token = loginForToken(email, password);
		return request -> {
			request.addHeader("Authorization", "Bearer " + token);
			return request;
		};
	}

	private String loginForToken(String email, String password) throws Exception {
		MvcTestResult result = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, password)).exchange();
		assertThat(result).hasStatus(HttpStatus.OK);
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
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
