package com.example.helpdesk.activity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.ticket.TicketService;
import com.example.helpdesk.user.User;
import com.example.helpdesk.user.UserRepository;
import com.example.helpdesk.user.UserResponse;

@Service
@Transactional(readOnly = true)
public class TicketActivityService {

	private final TicketActivityRepository activities;

	private final UserRepository users;

	private final TicketService ticketService;

	public TicketActivityService(TicketActivityRepository activities, UserRepository users,
			TicketService ticketService) {
		this.activities = activities;
		this.users = users;
		this.ticketService = ticketService;
	}

	public List<ActivityResponse> list(Long ticketId, HelpdeskUserDetails me) {
		// Same visibility as the ticket itself: 404 for a customer who doesn't own it.
		ticketService.get(ticketId, me);

		List<TicketActivity> history = activities.findByTicketIdOrderByOccurredAtAscIdAsc(ticketId);
		// One query for all the actors, instead of one per history entry
		Map<Long, User> actors = users
			.findAllById(history.stream().map(TicketActivity::getActorId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));

		return history.stream().map(activity -> {
			User actor = actors.get(activity.getActorId());
			return new ActivityResponse(activity.getId(), activity.getType(),
					actor != null ? UserResponse.from(actor) : null, activity.getDetails(), activity.getOccurredAt());
		}).toList();
	}

}
