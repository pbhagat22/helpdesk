package com.example.helpdesk.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Creating a user with any role (e.g. agents). Restricted to admins in the next step.
public record CreateUserRequest(@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(max = 255) String name, @NotNull Role role, @NotBlank @Size(min = 8, max = 72) String password) {
}
