package com.example.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

// End-to-end test: the whole application (controllers, services, JPA, H2) with
// nothing mocked, walking through a typical ticket's life. Slower than the slice
// tests, so keep these few and focused on how the pieces fit together.
@SpringBootTest
@AutoConfigureMockMvc
class HelpdeskFlowTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void ticketLifecycle() throws Exception {
		long customerId = createAndGetId("/api/users", """
				{"email": "flow-customer@example.com", "name": "Carol", "role": "CUSTOMER"}
				""");
		long agentId = createAndGetId("/api/users", """
				{"email": "flow-agent@example.com", "name": "Dave", "role": "AGENT"}
				""");
		long ticketId = createAndGetId("/api/tickets", """
				{"title": "Printer on fire", "description": "Smoke everywhere", "priority": "URGENT", "reporterId": %d}
				""".formatted(customerId));

		assertThat(mvc.patch()
			.uri("/api/tickets/{id}", ticketId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "IN_PROGRESS", "assigneeId": %d}
					""".formatted(agentId)))
			.hasStatus(HttpStatus.OK);

		createAndGetId("/api/tickets/" + ticketId + "/comments", """
				{"body": "Fire extinguisher deployed", "authorId": %d}
				""".formatted(agentId));

		assertThat(mvc.get().uri("/api/tickets?status=IN_PROGRESS&assigneeId={id}", agentId))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[0].assignee.name")
			.isEqualTo("Dave");

		assertThat(mvc.get().uri("/api/tickets/{id}/comments", ticketId))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[0].body")
			.isEqualTo("Fire extinguisher deployed");
	}

	private long createAndGetId(String uri, String json) throws Exception {
		MvcTestResult result = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
	}

}
