package com.example.helpdesk.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.LoginRequest;
import com.example.helpdesk.security.TokenResponse;
import com.example.helpdesk.security.TokenService;

import jakarta.validation.Valid;

// Public endpoints (see SecurityConfig): no login needed.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;

	private final TokenService tokenService;

	public AuthController(UserService userService, TokenService tokenService) {
		this.userService = userService;
		this.tokenService = tokenService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return userService.register(request);
	}

	// Exchanges email + password for a token. Send it on later requests as
	// "Authorization: Bearer <accessToken>".
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return tokenService.login(request);
	}

}
