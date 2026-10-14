package com.example.helpdesk.ticket;

// authorId comes from the request body until Spring Security (stage 4).
public record CreateCommentRequest(String body, Long authorId) {
}
