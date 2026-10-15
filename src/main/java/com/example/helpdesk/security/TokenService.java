package com.example.helpdesk.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

// Checks email + password once, then issues a signed token the client sends instead.
@Service
public class TokenService {

	private final AuthenticationManager authenticationManager;

	private final JwtEncoder jwtEncoder;

	private final Duration tokenLifetime;

	public TokenService(HelpdeskUserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
			JwtEncoder jwtEncoder, @Value("${helpdesk.jwt.ttl:PT1H}") Duration tokenLifetime) {
		// Loads the user by email and compares the password with the stored BCrypt hash.
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		this.authenticationManager = new ProviderManager(provider);
		this.jwtEncoder = jwtEncoder;
		this.tokenLifetime = tokenLifetime;
	}

	// Throws BadCredentialsException (→ 401) for an unknown email or a wrong password.
	// Both cases fail the same way, so callers can't probe which emails are registered.
	public TokenResponse login(LoginRequest request) {
		Authentication authentication = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		HelpdeskUserDetails user = (HelpdeskUserDetails) authentication.getPrincipal();

		Instant now = Instant.now();
		// The claims are readable by anyone holding the token (it's only base64), so
		// never put secrets in them. The signature makes them tamper-proof, not private.
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(SecurityConfig.ISSUER)
			.subject(user.getId().toString())
			.claim("email", user.getUsername())
			.claim("role", user.getRole().name())
			.issuedAt(now)
			.expiresAt(now.plus(tokenLifetime))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", tokenLifetime.toSeconds());
	}

}
