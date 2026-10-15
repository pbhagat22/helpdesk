package com.example.helpdesk.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Self sign-up: always creates a CUSTOMER. The password's max of 72 is BCrypt's limit
// (it ignores anything after 72 bytes).
public record RegisterRequest(@NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 255) String name,
		@NotBlank @Size(min = 8, max = 72) String password) {
}
