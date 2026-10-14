package com.example.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// reporterId comes from the request body until Spring Security (roadmap stage 4)
// lets us take it from the logged-in user.
public record CreateTicketRequest(@NotBlank @Size(max = 255) String title,
		@NotBlank @Size(max = 4000) String description, @NotNull Priority priority, @NotNull Long reporterId) {
}
