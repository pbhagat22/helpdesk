package com.example.helpdesk.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return userService.create(request);
	}

	@GetMapping
	public List<UserResponse> list() {
		return userService.list();
	}

	// The logged-in user. @AuthenticationPrincipal injects whoever Spring Security authenticated.
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal HelpdeskUserDetails me) {
		return userService.get(me.getId());
	}

	@GetMapping("/{id}")
	public UserResponse get(@PathVariable Long id) {
		return userService.get(id);
	}

}
