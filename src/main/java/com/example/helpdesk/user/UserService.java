package com.example.helpdesk.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.common.NotFoundException;

@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository users;

	// Constructor injection: Spring passes in the repository bean.
	public UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		User user = users.save(new User(request.email(), request.name(), request.role()));
		return UserResponse.from(user);
	}

	public List<UserResponse> list() {
		return users.findAll().stream().map(UserResponse::from).toList();
	}

	public UserResponse get(Long id) {
		return users.findById(id).map(UserResponse::from).orElseThrow(() -> new NotFoundException("User", id));
	}

}
