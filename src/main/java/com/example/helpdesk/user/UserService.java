package com.example.helpdesk.user;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.ConflictException;
import com.example.helpdesk.common.NotFoundException;

@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	// Constructor injection: Spring passes in the repository and encoder beans.
	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		return createUser(request.email(), request.name(), Role.CUSTOMER, request.password());
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		return createUser(request.email(), request.name(), request.role(), request.password());
	}

	// Returns true if the admin was created, false if a user with that email already exists.
	@Transactional
	public boolean createAdminIfMissing(String email, String password) {
		if (users.existsByEmail(email)) {
			return false;
		}
		createUser(email, "Administrator", Role.ADMIN, password);
		return true;
	}

	// Admins can't change their own role: whoever makes a change stays an admin, so there's
	// always at least one admin left. The new role reaches the user's login token only when
	// they log in again (the old token keeps the old role until it expires).
	@Transactional
	public UserResponse changeRole(Long id, Role role, Long actorId) {
		if (id.equals(actorId)) {
			throw new BadRequestException("You can't change your own role");
		}
		User user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
		user.setRole(role);
		return UserResponse.from(user);
	}

	public List<UserResponse> list() {
		return users.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
	}

	public UserResponse get(Long id) {
		return users.findById(id).map(UserResponse::from).orElseThrow(() -> new NotFoundException("User", id));
	}

	private UserResponse createUser(String email, String name, Role role, String password) {
		if (users.existsByEmail(email)) {
			throw new ConflictException("Email " + email + " is already registered");
		}
		User user = users.save(new User(email, name, role, passwordEncoder.encode(password)));
		return UserResponse.from(user);
	}

}
