package com.example.helpdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	// Every HTTP request passes through this chain of filters before reaching a controller.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			// Open to everyone: sign-up, the health check (used by Docker/AWS), and
			// Spring's error page (so error responses aren't replaced by a 401)
			.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
			.requestMatchers("/actuator/health/**", "/error").permitAll()
			// Everything else requires a logged-in user
			.anyRequest().authenticated())
			// HTTP Basic: the client sends "Authorization: Basic base64(email:password)"
			// with every request. Simple, but only safe over HTTPS.
			.httpBasic(Customizer.withDefaults())
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
