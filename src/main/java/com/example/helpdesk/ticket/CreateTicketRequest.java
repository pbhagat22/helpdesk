package com.example.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// The reporter is the logged-in user, so it isn't part of the request.
public record CreateTicketRequest(@NotBlank @Size(max = 255) String title,
		@NotBlank @Size(max = 4000) String description, @NotNull Priority priority) {
}
