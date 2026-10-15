package com.example.helpdesk.security;

// expiresIn is in seconds, as in the OAuth 2.0 token response format.
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
