package com.example.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// authorId comes from the request body until Spring Security (roadmap stage 4).
public record CreateCommentRequest(@NotBlank @Size(max = 4000) String body, @NotNull Long authorId) {
}
