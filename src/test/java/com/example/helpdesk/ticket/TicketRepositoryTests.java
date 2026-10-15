package com.example.helpdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.example.helpdesk.TestcontainersConfiguration;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;

// @DataJpaTest starts only the JPA layer (no web server) against a Postgres
// Testcontainers database, and rolls back each test's transaction so tests don't affect each other.
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TicketRepositoryTests {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private TicketRepository tickets;

	@Autowired
	private CommentRepository comments;

	private User customer;

	private User agent;

	@BeforeEach
	void setUp() {
		customer = entityManager.persist(new User("alice@example.com", "Alice", Role.CUSTOMER, "{noop}unused"));
		agent = entityManager.persist(new User("bob@example.com", "Bob", Role.AGENT, "{noop}unused"));
	}

	@Test
	void findsTicketsByStatus() {
		Ticket open = entityManager.persist(new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer));
		Ticket resolved = new Ticket("Typo on invoice", "Wrong company name", Priority.LOW, customer);
		resolved.setStatus(TicketStatus.RESOLVED);
		entityManager.persist(resolved);

		assertThat(tickets.findByStatusOrderByCreatedAtDesc(TicketStatus.OPEN)).containsExactly(open);
	}

	@Test
	void findsTicketsAssignedToAgent() {
		Ticket assigned = new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer);
		assigned.setAssignee(agent);
		entityManager.persist(assigned);
		entityManager.persist(new Ticket("Unassigned", "Nobody has this yet", Priority.LOW, customer));

		assertThat(tickets.findByAssigneeIdOrderByCreatedAtDesc(agent.getId())).containsExactly(assigned);
	}

	@Test
	void findsCommentsForTicketOldestFirst() {
		Ticket ticket = entityManager.persist(new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer));
		Comment first = entityManager.persist(new Comment(ticket, customer, "Still broken"));
		Comment second = entityManager.persist(new Comment(ticket, agent, "Looking into it"));

		assertThat(comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId())).containsExactly(first, second);
	}

}
