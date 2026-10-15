package com.example.helpdesk.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Security filters run before Spring MVC, so @RestControllerAdvice never sees their
// errors. This hands them to MVC's exception resolver, which calls GlobalExceptionHandler,
// so 401/403 responses get the same ProblemDetail JSON as every other error.
class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final HandlerExceptionResolver exceptionResolver;

	SecurityProblemHandler(HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	// Not logged in, or wrong email/password
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
		exceptionResolver.resolveException(request, response, null, ex);
	}

	// Logged in, but not allowed
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
		exceptionResolver.resolveException(request, response, null, ex);
	}

}
