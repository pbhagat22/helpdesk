package com.example.helpdesk.user;

public record CreateUserRequest(String email, String name, Role role) {
}
