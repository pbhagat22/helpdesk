package com.example.helpdesk.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

// @EnableMethodSecurity turns on @PreAuthorize, used on controller methods for role rules.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private final SecurityProblemHandler problemHandler;

	// Spring MVC's exception resolver, so security errors are rendered by GlobalExceptionHandler
	// like every other error (see SecurityProblemHandler).
	public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.problemHandler = new SecurityProblemHandler(exceptionResolver);
	}

	// Every HTTP request passes through this chain of filters before reaching a controller.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			// Open to everyone: sign-up, the health check (used by Docker/AWS), and
			// Spring's error page (so error responses aren't replaced by a 401)
			.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
			.requestMatchers("/actuator/health/**", "/error").permitAll()
			// Everything else requires a logged-in user. Which roles may call what is
			// decided per endpoint with @PreAuthorize on the controller methods.
			.anyRequest().authenticated())
			// HTTP Basic: the client sends "Authorization: Basic base64(email:password)"
			// with every request. Simple, but only safe over HTTPS.
			.httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))
			// Missing/wrong credentials (401) and missing permissions (403) as ProblemDetail JSON
			.exceptionHandling(
					errors -> errors.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
			// Stateless: no server-side session or cookie; each request authenticates itself
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// CSRF attacks abuse cookies the browser sends automatically. With no session
			// cookie, there's nothing to abuse, so the protection isn't needed here.
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	// Hashes new passwords with BCrypt (prefixed "{bcrypt}"), and can still verify hashes
	// from other algorithms if the default ever changes.
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
