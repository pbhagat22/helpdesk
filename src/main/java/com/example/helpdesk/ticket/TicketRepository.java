package com.example.helpdesk.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

	// One query for every combination of optional filters: a null parameter means
	// "don't filter on this". Written in JPQL (entity and field names, not tables
	// and columns), which Hibernate translates to SQL.
	// @EntityGraph loads reporter and assignee in the same query (a join),
	// instead of one extra query per ticket when they are read later.
	@EntityGraph(attributePaths = { "reporter", "assignee" })
	@Query("""
			select t from Ticket t
			where (:status is null or t.status = :status)
			  and (:assigneeId is null or t.assignee.id = :assigneeId)
			  and (:reporterId is null or t.reporter.id = :reporterId)
			order by t.createdAt desc, t.id desc
			""")
	List<Ticket> search(TicketStatus status, Long assigneeId, Long reporterId);

}
