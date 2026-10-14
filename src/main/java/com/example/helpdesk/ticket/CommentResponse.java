package com.example.helpdesk.ticket;

import java.time.Instant;

import com.example.helpdesk.user.UserResponse;

public record CommentResponse(Long id, Long ticketId, UserResponse author, String body, Instant createdAt) {

	public static CommentResponse from(Comment comment) {
		return new CommentResponse(comment.getId(), comment.getTicket().getId(), UserResponse.from(comment.getAuthor()),
				comment.getBody(), comment.getCreatedAt());
	}

}
