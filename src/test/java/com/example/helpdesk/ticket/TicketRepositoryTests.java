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
	void searchWithoutFiltersReturnsAllNewestFirst() {
		Ticket older = entityManager.persist(new Ticket("Older", "First one", Priority.LOW, customer));
		Ticket newer = entityManager.persist(new Ticket("Newer", "Second one", Priority.LOW, customer));

		assertThat(tickets.search(null, null, null)).containsExactly(newer, older);
	}

	@Test
	void searchFiltersByStatus() {
		Ticket open = entityManager.persist(new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer));
		Ticket resolved = new Ticket("Typo on invoice", "Wrong company name", Priority.LOW, customer);
		resolved.setStatus(TicketStatus.RESOLVED);
		entityManager.persist(resolved);

		assertThat(tickets.search(TicketStatus.OPEN, null, null)).containsExactly(open);
	}

	@Test
	void searchFiltersByAssignee() {
		Ticket assigned = new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer);
		assigned.setAssignee(agent);
		entityManager.persist(assigned);
		entityManager.persist(new Ticket("Unassigned", "Nobody has this yet", Priority.LOW, customer));

		assertThat(tickets.search(null, agent.getId(), null)).containsExactly(assigned);
	}

	@Test
	void searchFiltersByReporterAndStatusTogether() {
		User otherCustomer = entityManager.persist(new User("carol@example.com", "Carol", Role.CUSTOMER, "{noop}unused"));
		Ticket mine = entityManager.persist(new Ticket("Mine", "Alice's ticket", Priority.LOW, customer));
		entityManager.persist(new Ticket("Theirs", "Carol's ticket", Priority.LOW, otherCustomer));
		Ticket mineResolved = new Ticket("Mine, done", "Alice's old ticket", Priority.LOW, customer);
		mineResolved.setStatus(TicketStatus.RESOLVED);
		entityManager.persist(mineResolved);

		assertThat(tickets.search(TicketStatus.OPEN, null, customer.getId())).containsExactly(mine);
	}

	@Test
	void findsCommentsForTicketOldestFirst() {
		Ticket ticket = entityManager.persist(new Ticket("Can't log in", "Password reset fails", Priority.HIGH, customer));
		Comment first = entityManager.persist(new Comment(ticket, customer, "Still broken"));
		Comment second = entityManager.persist(new Comment(ticket, agent, "Looking into it"));

		assertThat(comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId())).containsExactly(first, second);
	}

}
