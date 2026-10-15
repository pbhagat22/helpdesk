package com.example.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The author is the logged-in user, so it isn't part of the request.
public record CreateCommentRequest(@NotBlank @Size(max = 4000) String body) {
}
