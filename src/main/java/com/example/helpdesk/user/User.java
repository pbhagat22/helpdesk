package com.example.helpdesk.user;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// "user" is a reserved word in H2 and Postgres, so the table is named "users".
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	// A BCrypt hash such as "{bcrypt}$2a$10$...", never the plain password.
	// The "{bcrypt}" prefix tells Spring Security which algorithm produced it.
	@Column(nullable = false, length = 100)
	private String passwordHash;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	// JPA needs a no-arg constructor; protected keeps application code from using it.
	protected User() {
	}

	public User(String email, String name, Role role, String passwordHash) {
		this.email = email;
		this.name = name;
		this.role = role;
		this.passwordHash = passwordHash;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
