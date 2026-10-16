package com.example.helpdesk.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

	// Creating accounts with a chosen role (e.g. agents) is for admins only.
	// Customers sign themselves up through /api/auth/register.
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return userService.create(request);
	}

	// Staff need the user list, e.g. to pick an assignee. Customers don't.
	@GetMapping
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public List<UserResponse> list() {
		return userService.list();
	}

	// The logged-in user. @AuthenticationPrincipal injects whoever Spring Security authenticated.
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal HelpdeskUserDetails me) {
		return userService.get(me.getId());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public UserResponse get(@PathVariable Long id) {
		return userService.get(id);
	}

	// PATCH /api/users/5 {"role": "AGENT"}: promote or demote a user (admins only)
	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest request,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return userService.changeRole(id, request.role(), me.getId());
	}

}
