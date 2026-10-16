package com.example.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.helpdesk.search.TicketSearchService;
import com.example.helpdesk.ticket.TicketEventPublisher;
import com.jayway.jsonpath.JsonPath;

// End-to-end test: the whole application (security, controllers, services, JPA, Postgres)
// with nothing mocked, walking through a typical ticket's life. Users log in for real
// and send the token they get back, as the React UI will. Slower than
// the slice tests, so keep these few and focused on how the pieces fit together.
// The properties configure the bootstrap admin, as HELPDESK_ADMIN_* would in production.
@SpringBootTest(properties = { "helpdesk.admin.email=admin@example.com", "helpdesk.admin.password=admin-password" })
@Import({ TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class,
		ElasticsearchTestcontainersConfiguration.class })
@AutoConfigureMockMvc
class HelpdeskFlowTests {

	@Autowired
	private MockMvcTester mvc;

	// Spring Boot's Kafka consumer settings, pointing at the test container
	@Autowired
	private ConsumerFactory<?, ?> consumerFactory;

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
		assertThat(mvc.get().uri("/api/tickets/{id}/activity", erinsTicket).with(frank))
			.hasStatus(HttpStatus.NOT_FOUND);
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

	// The role lives inside the login token, so a role change only takes effect with a new
	// token: the old one keeps the old role until it expires.
	@Test
	void promotedUserGetsNewRightsAfterLoggingInAgain() throws Exception {
		RequestPostProcessor admin = login("admin@example.com", "admin-password");
		RequestPostProcessor oldToken = register("flow-lena@example.com", "Lena", "lena-password");
		long lenaId = JsonPath.<Number>read(
				mvc.get().uri("/api/users/me").with(oldToken).exchange().getResponse().getContentAsString(), "$.id")
			.longValue();

		assertThat(mvc.patch().uri("/api/users/{id}", lenaId).with(admin).contentType(MediaType.APPLICATION_JSON).content("""
				{"role": "AGENT"}
				"""))
			.hasStatus(HttpStatus.OK);

		// Staff-only endpoint: the old token still says CUSTOMER...
		assertThat(mvc.get().uri("/api/users").with(oldToken)).hasStatus(HttpStatus.FORBIDDEN);
		// ...a fresh login carries AGENT.
		RequestPostProcessor newToken = login("flow-lena@example.com", "lena-password");
		assertThat(mvc.get().uri("/api/users").with(newToken)).hasStatus(HttpStatus.OK);
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

	// Changes made through the API reach Kafka as events, in order, after each commit.
	@Test
	void ticketChangesArePublishedToKafka() throws Exception {
		RequestPostProcessor admin = login("admin@example.com", "admin-password");
		RequestPostProcessor hana = register("flow-hana@example.com", "Hana", "hana-password");
		long ticketId = createAndGetId("/api/tickets", hana, """
				{"title": "Laptop won't boot", "description": "Black screen", "priority": "HIGH"}
				""");
		long adminId = JsonPath.<Number>read(
				mvc.get().uri("/api/users/me").with(admin).exchange().getResponse().getContentAsString(), "$.id")
			.longValue();
		assertThat(mvc.patch()
			.uri("/api/tickets/{id}", ticketId)
			.with(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "IN_PROGRESS", "assigneeId": %d}
					""".formatted(adminId)))
			.hasStatus(HttpStatus.OK);
		createAndGetId("/api/tickets/" + ticketId + "/comments", admin, """
				{"body": "Looking into it"}
				""");

		assertThat(readEventTypes(ticketId, 4)).containsExactly("CREATED", "STATUS_CHANGED", "ASSIGNED", "COMMENTED");

		// The app's own listener turns the same events into the ticket's history. That happens
		// in the background, so wait (up to 15s) until it's there rather than expecting it at once.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
				mvc.get().uri("/api/tickets/{id}/activity", ticketId).with(hana))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[*].type")
			.asArray()
			.containsExactly("CREATED", "STATUS_CHANGED", "ASSIGNED", "COMMENTED"));
		assertThat(mvc.get().uri("/api/tickets/{id}/activity", ticketId).with(hana))
			.bodyJson()
			.extractingPath("$[1].details.to")
			.isEqualTo("IN_PROGRESS");
	}

	// Search goes through Kafka (event) → indexer → Elasticsearch, so results appear after a
	// moment; each check waits for them.
	@Test
	void searchFindsTicketsByMeaningAndKeepsCustomersApart() throws Exception {
		RequestPostProcessor admin = login("admin@example.com", "admin-password");
		RequestPostProcessor ivy = register("flow-ivy@example.com", "Ivy", "ivy-password");
		RequestPostProcessor jack = register("flow-jack@example.com", "Jack", "jack-password");
		long ivysTicket = createAndGetId("/api/tickets", ivy, """
				{"title": "Office printer jammed", "description": "Paper stuck and it smells of smoke", "priority": "HIGH"}
				""");
		long jacksTicket = createAndGetId("/api/tickets", jack, """
				{"title": "Monitor flickering", "description": "The display goes dark every few minutes", "priority": "LOW"}
				""");
		createAndGetId("/api/tickets/" + ivysTicket + "/comments", admin, """
				{"body": "Ordered a replacement toner cartridge"}
				""");

		// One word per search, so each case is tested on its own (with several words, any one
		// matching is enough). Word forms: "smoking" matches "smoke" (same stem).
		awaitSearchIds(ivy, "smoking", ids -> assertThat(ids).containsExactly(ivysTicket));
		// Prefixes: "print" and "printing" (stem "print") find "printer"
		awaitSearchIds(ivy, "print", ids -> assertThat(ids).containsExactly(ivysTicket));
		awaitSearchIds(ivy, "printing", ids -> assertThat(ids).containsExactly(ivysTicket));
		// A typo
		awaitSearchIds(ivy, "jamed", ids -> assertThat(ids).containsExactly(ivysTicket));
		// Text that's only in a comment (indexed after the COMMENTED event)
		awaitSearchIds(ivy, "toner", ids -> assertThat(ids).containsExactly(ivysTicket));
		// Matched words come back marked, for highlighting in the UI
		assertThat(mvc.get().uri("/api/tickets/search?q=toner").with(ivy))
			.bodyJson()
			.extractingPath("$[0].highlights[0]")
			.asString()
			.contains(TicketSearchService.MARK_START + "toner" + TicketSearchService.MARK_END);

		// Customers only find their own tickets; staff find everyone's.
		awaitSearchIds(admin, "flickering display", ids -> assertThat(ids).contains(jacksTicket));
		assertThat(searchIds(jack, "printer toner")).doesNotContain(ivysTicket);
		assertThat(searchIds(ivy, "flickering display")).doesNotContain(jacksTicket);

		assertThat(mvc.get().uri("/api/tickets/search?q=").with(ivy)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void onlyAdminsCanRebuildTheSearchIndex() throws Exception {
		RequestPostProcessor admin = login("admin@example.com", "admin-password");
		RequestPostProcessor kim = register("flow-kim@example.com", "Kim", "kim-password");
		createAndGetId("/api/tickets", kim, """
				{"title": "Reindex me", "description": "Anything", "priority": "LOW"}
				""");

		assertThat(mvc.post().uri("/api/admin/search/reindex").with(kim)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/admin/search/reindex").with(admin)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.indexed")
			.asNumber()
			.satisfies(count -> assertThat(count.intValue()).isPositive());
	}

	private List<Long> searchIds(RequestPostProcessor user, String query) throws Exception {
		MvcTestResult result = mvc.get().uri("/api/tickets/search").param("q", query).with(user).exchange();
		assertThat(result).hasStatus(HttpStatus.OK);
		List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].ticket.id");
		return ids.stream().map(Number::longValue).toList();
	}

	private void awaitSearchIds(RequestPostProcessor user, String query,
			java.util.function.Consumer<List<Long>> assertion) {
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertion.accept(searchIds(user, query)));
	}

	// Reads the topic from the beginning with a fresh consumer group, keeping the events for
	// one ticket (the message key), until `count` have arrived or 15 seconds have passed.
	private List<String> readEventTypes(long ticketId, int count) {
		Map<String, Object> props = new HashMap<>(consumerFactory.getConfigurationProperties());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "flow-test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		List<String> types = new ArrayList<>();
		try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(TicketEventPublisher.TOPIC));
			Instant deadline = Instant.now().plusSeconds(15);
			while (types.size() < count && Instant.now().isBefore(deadline)) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
					if (record.key().equals(String.valueOf(ticketId))) {
						types.add(JsonPath.read(record.value(), "$.type"));
					}
				}
			}
		}
		return types;
	}

	// The React app (here a stand-in from src/test/resources/static) loads without a login,
	// and browser-only routes like /tickets/5 get index.html so React Router can handle them.
	@Test
	void uiIsServedWithoutLoginIncludingDeepLinks() {
		// Spring Boot answers "/" with a server-side forward to index.html (its "welcome page").
		// MockMvc records forwards instead of following them, so check the forward itself.
		assertThat(mvc.get().uri("/")).hasStatus(HttpStatus.OK).hasForwardedUrl("index.html");
		assertThat(mvc.get().uri("/tickets/5")).hasStatus(HttpStatus.OK).bodyText().contains("test-spa");
		assertThat(mvc.get().uri("/login")).hasStatus(HttpStatus.OK).bodyText().contains("test-spa");
	}

	@Test
	void unknownApiPathsAre404NotTheUi() throws Exception {
		RequestPostProcessor admin = login("admin@example.com", "admin-password");

		assertThat(mvc.get().uri("/api/no-such-thing").with(admin)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyText()
			.doesNotContain("test-spa");
		// ...and the API still needs a login, even though the UI doesn't.
		assertThat(mvc.get().uri("/api/tickets")).hasStatus(HttpStatus.UNAUTHORIZED);
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
