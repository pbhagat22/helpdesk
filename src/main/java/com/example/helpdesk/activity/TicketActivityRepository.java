package com.example.helpdesk.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {

	boolean existsByEventId(UUID eventId);

	List<TicketActivity> findByTicketIdOrderByOccurredAtAscIdAsc(Long ticketId);

}
