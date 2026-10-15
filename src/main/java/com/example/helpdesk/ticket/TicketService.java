package com.example.helpdesk.ticket;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;
import com.example.helpdesk.user.UserRepository;

// Entities are converted to response DTOs inside the transaction, so lazy
// relationships (reporter, assignee, author) can still be loaded.
//
// Role rules (who may call an endpoint at all) are @PreAuthorize on the controller.
// Ownership rules depend on the data, so they live here: customers only see their own
// tickets, while agents and admins see all of them.
@Service
@Transactional(readOnly = true)
public class TicketService {

	private final TicketRepository tickets;

	private final CommentRepository comments;

	private final UserRepository users;

	// Spring's in-process event bus. TicketEventPublisher forwards the events to Kafka after
	// the transaction commits, so this service doesn't need to know about Kafka at all.
	private final ApplicationEventPublisher events;

	public TicketService(TicketRepository tickets, CommentRepository comments, UserRepository users,
			ApplicationEventPublisher events) {
		this.tickets = tickets;
		this.comments = comments;
		this.users = users;
		this.events = events;
	}

	@Transactional
	public TicketResponse create(CreateTicketRequest request, Long reporterId) {
		User reporter = findUser(reporterId);
		Ticket ticket = tickets
			.save(new Ticket(request.title(), request.description(), request.priority(), reporter));
		events.publishEvent(TicketEvent.of(TicketEventType.CREATED, ticket.getId(), reporterId,
				Map.of("title", ticket.getTitle(), "priority", ticket.getPriority().name())));
		return TicketResponse.from(ticket);
	}

	public List<TicketResponse> list(TicketStatus status, Long assigneeId, HelpdeskUserDetails me) {
		// Customers always get only their own tickets, whatever filters they pass.
		Long reporterId = isCustomer(me) ? me.getId() : null;
		return tickets.search(status, assigneeId, reporterId).stream().map(TicketResponse::from).toList();
	}

	public TicketResponse get(Long id, HelpdeskUserDetails me) {
		return TicketResponse.from(findVisibleTicket(id, me));
	}

	// Only agents and admins get here (see TicketController), so no ownership check.
	// No save() call needed: changes to an entity loaded inside a transaction
	// are written to the database automatically when the transaction commits.
	// Only real changes produce events: setting a field to the value it already has doesn't.
	@Transactional
	public TicketResponse update(Long id, UpdateTicketRequest request, HelpdeskUserDetails me) {
		Ticket ticket = findTicket(id);
		if (request.status() != null && request.status() != ticket.getStatus()) {
			events.publishEvent(TicketEvent.of(TicketEventType.STATUS_CHANGED, id, me.getId(),
					Map.of("from", ticket.getStatus().name(), "to", request.status().name())));
			ticket.setStatus(request.status());
		}
		if (request.priority() != null && request.priority() != ticket.getPriority()) {
			events.publishEvent(TicketEvent.of(TicketEventType.PRIORITY_CHANGED, id, me.getId(),
					Map.of("from", ticket.getPriority().name(), "to", request.priority().name())));
			ticket.setPriority(request.priority());
		}
		if (request.assigneeId() != null) {
			User assignee = findUser(request.assigneeId());
			if (assignee.getRole() == Role.CUSTOMER) {
				throw new BadRequestException("Tickets can only be assigned to agents or admins");
			}
			if (ticket.getAssignee() == null || !ticket.getAssignee().getId().equals(assignee.getId())) {
				events.publishEvent(TicketEvent.of(TicketEventType.ASSIGNED, id, me.getId(),
						Map.of("assigneeId", assignee.getId().toString(), "assigneeName", assignee.getName())));
				ticket.setAssignee(assignee);
			}
		}
		// Write the changes now so @UpdateTimestamp sets updatedAt before we build the response.
		tickets.flush();
		return TicketResponse.from(ticket);
	}

	public List<CommentResponse> listComments(Long ticketId, HelpdeskUserDetails me) {
		findVisibleTicket(ticketId, me);
		return comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream().map(CommentResponse::from).toList();
	}

	@Transactional
	public CommentResponse addComment(Long ticketId, CreateCommentRequest request, HelpdeskUserDetails me) {
		Ticket ticket = findVisibleTicket(ticketId, me);
		User author = findUser(me.getId());
		Comment comment = comments.save(new Comment(ticket, author, request.body()));
		events.publishEvent(TicketEvent.of(TicketEventType.COMMENTED, ticketId, me.getId(),
				Map.of("commentId", comment.getId().toString())));
		return CommentResponse.from(comment);
	}

	// A customer asking for someone else's ticket gets 404, not 403: a 403 would
	// confirm that the ticket exists, which is already more than they should learn.
	private Ticket findVisibleTicket(Long id, HelpdeskUserDetails me) {
		Ticket ticket = findTicket(id);
		if (isCustomer(me) && !ticket.getReporter().getId().equals(me.getId())) {
			throw new NotFoundException("Ticket", id);
		}
		return ticket;
	}

	private static boolean isCustomer(HelpdeskUserDetails me) {
		return me.getRole() == Role.CUSTOMER;
	}

	private Ticket findTicket(Long id) {
		return tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket", id));
	}

	private User findUser(Long id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
	}

}
