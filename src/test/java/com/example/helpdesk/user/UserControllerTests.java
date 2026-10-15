package com.example.helpdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.common.ConflictException;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.security.SecurityConfig;

// @WebMvcTest starts only the web layer for these controllers (plus @RestControllerAdvice
// classes like GlobalExceptionHandler). SecurityConfig is imported so the real security
// rules apply. The service is a Mockito mock, so these tests check HTTP concerns:
// URLs, JSON, status codes, validation and who may call what.
@WebMvcTest({ UserController.class, AuthController.class })
@Import(SecurityConfig.class)
class UserControllerTests {

	// with(user(alice)) makes a request run as this logged-in user, without a real login.
	private static final HelpdeskUserDetails alice = new HelpdeskUserDetails(1L, "alice@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails admin = new HelpdeskUserDetails(9L, "admin@example.com", "unused",
			Role.ADMIN);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserService userService;

	private UserResponse aliceResponse() {
		return new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER, Instant.now());
	}

	@Test
	void registerIsPublicAndReturns201() {
		given(userService.register(any())).willReturn(aliceResponse());

		assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "alice@example.com", "name": "Alice", "password": "correct-horse"}
				"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.role")
			.isEqualTo("CUSTOMER");
	}

	@Test
	void registerRejectsShortPassword() {
		assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "alice@example.com", "name": "Alice", "password": "short"}
				"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asMap()
			.containsOnlyKeys("password");
	}

	@Test
	void endpointsRequireLogin() {
		assertThat(mvc.get().uri("/api/users")).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(userService);
	}

	@Test
	void meReturnsLoggedInUser() {
		given(userService.get(1L)).willReturn(aliceResponse());

		assertThat(mvc.get().uri("/api/users/me").with(user(alice)))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo("alice@example.com");
	}

	@Test
	void createReturns400WithFieldErrorsForInvalidInput() {
		assertThat(mvc.post().uri("/api/users").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "not-an-email", "name": "", "role": null, "password": ""}
				"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asMap()
			.containsOnlyKeys("email", "name", "role", "password");

		// Validation fails before the controller method runs.
		verifyNoInteractions(userService);
	}

	@Test
	void createIsForbiddenForNonAdmins() {
		assertThat(mvc.post().uri("/api/users").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "bob@example.com", "name": "Bob", "role": "AGENT", "password": "correct-horse"}
				"""))
			.hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(userService);
	}

	@Test
	void listIsForbiddenForCustomers() {
		assertThat(mvc.get().uri("/api/users").with(user(alice))).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void createReturns409ForDuplicateEmail() {
		given(userService.create(any())).willThrow(new ConflictException("Email bob@example.com is already registered"));

		assertThat(mvc.post().uri("/api/users").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "bob@example.com", "name": "Bob", "role": "AGENT", "password": "correct-horse"}
				"""))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Email bob@example.com is already registered");
	}

}
