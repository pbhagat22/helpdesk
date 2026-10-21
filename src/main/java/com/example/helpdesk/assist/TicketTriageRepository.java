package com.example.helpdesk.assist;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTriageRepository extends JpaRepository<TicketTriage, Long> {

	boolean existsByTicketId(Long ticketId);

	Optional<TicketTriage> findByTicketId(Long ticketId);

}
