package com.example.helpdesk.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.helpdesk.user.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

// @EnableMethodSecurity turns on @PreAuthorize, used on controller methods for role rules.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	static final String ISSUER = "helpdesk";

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private final SecurityProblemHandler problemHandler;

	// The key that signs and verifies tokens (HMAC-SHA256: one shared secret does both).
	private final SecretKey jwtKey;

	// The exception resolver is Spring MVC's, so security errors are rendered by
	// GlobalExceptionHandler like every other error (see SecurityProblemHandler).
	public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
			@Value("${helpdesk.jwt.secret:}") String jwtSecret) {
		this.problemHandler = new SecurityProblemHandler(exceptionResolver);
		this.jwtKey = signingKey(jwtSecret);
	}

	// Every HTTP request passes through this chain of filters before reaching a controller.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			// Open to everyone: sign-up, login, the health check (used by Docker/AWS), and
			// Spring's error page (so error responses aren't replaced by a 401)
			.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
			.requestMatchers("/actuator/health/**", "/error").permitAll()
			// The rest of the API requires a logged-in user. Which roles may call what is
			// decided per endpoint with @PreAuthorize on the controller methods.
			.requestMatchers("/api/**").authenticated()
			// Any other Actuator endpoint someone enables later: admins only
			.requestMatchers("/actuator/**").hasRole("ADMIN")
			// Everything else is the React app (HTML, JS, CSS). It must load before anyone
			// has logged in, and contains no data: all data comes from the protected API.
			.anyRequest().permitAll())
			// Requests authenticate with "Authorization: Bearer <JWT>" from /api/auth/login.
			// Spring checks the signature, expiry and issuer, then calls toAuthentication.
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(SecurityConfig::toAuthentication))
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler))
			// Missing/invalid token (401) and missing permissions (403) as ProblemDetail JSON
			.exceptionHandling(
					errors -> errors.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
			// Stateless: no server-side session or cookie; each request carries its token
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// CSRF attacks abuse cookies the browser sends automatically. Tokens are sent
			// explicitly by the client in a header, so there's nothing to abuse.
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	// Turns a verified token into the logged-in user, so @AuthenticationPrincipal
	// HelpdeskUserDetails keeps working. Everything comes from the token's claims:
	// no database lookup per request.
	static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
		HelpdeskUserDetails user = new HelpdeskUserDetails(Long.valueOf(jwt.getSubject()),
				jwt.getClaimAsString("email"), "", Role.valueOf(jwt.getClaimAsString("role")));
		return UsernamePasswordAuthenticationToken.authenticated(user, jwt, user.getAuthorities());
	}

	@Bean
	JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	@Bean
	JwtDecoder jwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
		// Default checks (expiry, not-before) plus: the token must have been issued by us.
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	// Hashes new passwords with BCrypt (prefixed "{bcrypt}"), and can still verify hashes
	// from other algorithms if the default ever changes.
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	// Production sets HELPDESK_JWT_SECRET (at least 32 characters) so tokens stay valid
	// across restarts and across several app instances. Without it, a random key is made
	// at startup: fine for development and tests, but every restart logs everyone out.
	private static SecretKey signingKey(String secret) {
		byte[] bytes;
		if (secret.isBlank()) {
			log.warn("HELPDESK_JWT_SECRET not set: using a random key, tokens won't survive a restart");
			bytes = new byte[32];
			new SecureRandom().nextBytes(bytes);
		}
		else {
			bytes = secret.getBytes(StandardCharsets.UTF_8);
			if (bytes.length < 32) {
				throw new IllegalStateException("HELPDESK_JWT_SECRET must be at least 32 bytes for HS256");
			}
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
