package com.example.helpdesk.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;

// The logged-in user as Spring Security sees it. Controllers receive it with
// @AuthenticationPrincipal, which gives them the user's id without a database lookup.
public class HelpdeskUserDetails implements UserDetails {

	private final Long id;

	private final String email;

	private final String passwordHash;

	private final Role role;

	public HelpdeskUserDetails(Long id, String email, String passwordHash, Role role) {
		this.id = id;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	public static HelpdeskUserDetails from(User user) {
		return new HelpdeskUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
	}

	public Long getId() {
		return id;
	}

	public Role getRole() {
		return role;
	}

	// Spring Security checks roles as authorities named "ROLE_<name>", e.g. ROLE_AGENT.
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	// Users log in with their email address.
	@Override
	public String getUsername() {
		return email;
	}

}
