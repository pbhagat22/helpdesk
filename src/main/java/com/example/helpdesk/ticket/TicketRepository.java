package com.example.helpdesk.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

	// @EntityGraph loads reporter and assignee in the same query (a join),
	// instead of one extra query per ticket when they are read later.
	@EntityGraph(attributePaths = { "reporter", "assignee" })
	List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

	@EntityGraph(attributePaths = { "reporter", "assignee" })
	List<Ticket> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

}
