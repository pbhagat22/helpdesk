package com.example.helpdesk.ticket;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;
import com.example.helpdesk.user.UserRepository;

// Entities are converted to response DTOs inside the transaction, so lazy
// relationships (reporter, assignee, author) can still be loaded.
@Service
@Transactional(readOnly = true)
public class TicketService {

	private final TicketRepository tickets;

	private final CommentRepository comments;

	private final UserRepository users;

	public TicketService(TicketRepository tickets, CommentRepository comments, UserRepository users) {
		this.tickets = tickets;
		this.comments = comments;
		this.users = users;
	}

	@Transactional
	public TicketResponse create(CreateTicketRequest request) {
		User reporter = findUser(request.reporterId());
		Ticket ticket = tickets
			.save(new Ticket(request.title(), request.description(), request.priority(), reporter));
		return TicketResponse.from(ticket);
	}

	public List<TicketResponse> list(TicketStatus status, Long assigneeId) {
		List<Ticket> result;
		if (status != null && assigneeId != null) {
			result = tickets.findByStatusAndAssigneeIdOrderByCreatedAtDesc(status, assigneeId);
		}
		else if (status != null) {
			result = tickets.findByStatusOrderByCreatedAtDesc(status);
		}
		else if (assigneeId != null) {
			result = tickets.findByAssigneeIdOrderByCreatedAtDesc(assigneeId);
		}
		else {
			result = tickets.findAllByOrderByCreatedAtDesc();
		}
		return result.stream().map(TicketResponse::from).toList();
	}

	public TicketResponse get(Long id) {
		return TicketResponse.from(findTicket(id));
	}

	// No save() call needed: changes to an entity loaded inside a transaction
	// are written to the database automatically when the transaction commits.
	@Transactional
	public TicketResponse update(Long id, UpdateTicketRequest request) {
		Ticket ticket = findTicket(id);
		if (request.status() != null) {
			ticket.setStatus(request.status());
		}
		if (request.priority() != null) {
			ticket.setPriority(request.priority());
		}
		if (request.assigneeId() != null) {
			User assignee = findUser(request.assigneeId());
			if (assignee.getRole() == Role.CUSTOMER) {
				throw new BadRequestException("Tickets can only be assigned to agents or admins");
			}
			ticket.setAssignee(assignee);
		}
		// Write the changes now so @UpdateTimestamp sets updatedAt before we build the response.
		tickets.flush();
		return TicketResponse.from(ticket);
	}

	public List<CommentResponse> listComments(Long ticketId) {
		findTicket(ticketId);
		return comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream().map(CommentResponse::from).toList();
	}

	@Transactional
	public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
		Ticket ticket = findTicket(ticketId);
		User author = findUser(request.authorId());
		return CommentResponse.from(comments.save(new Comment(ticket, author, request.body())));
	}

	private Ticket findTicket(Long id) {
		return tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket", id));
	}

	private User findUser(Long id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
	}

}
