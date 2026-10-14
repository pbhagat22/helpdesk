package com.example.helpdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.common.ConflictException;

// @WebMvcTest starts only the web layer for UserController (plus @RestControllerAdvice
// classes like GlobalExceptionHandler). The service is replaced by a Mockito mock,
// so these tests check HTTP concerns: URLs, JSON, status codes, validation.
@WebMvcTest(UserController.class)
class UserControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserService userService;

	@Test
	void createReturns201WithUser() {
		given(userService.create(any()))
			.willReturn(new UserResponse(1L, "alice@example.com", "Alice", Role.CUSTOMER, Instant.now()));

		assertThat(mvc.post()
			.uri("/api/users")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "alice@example.com", "name": "Alice", "role": "CUSTOMER"}
					"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.id")
			.isEqualTo(1);
	}

	@Test
	void createReturns400WithFieldErrorsForInvalidInput() {
		assertThat(mvc.post()
			.uri("/api/users")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "not-an-email", "name": "", "role": null}
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asMap()
			.containsOnlyKeys("email", "name", "role");

		// Validation fails before the controller method runs.
		verifyNoInteractions(userService);
	}

	@Test
	void createReturns409ForDuplicateEmail() {
		given(userService.create(any())).willThrow(new ConflictException("Email alice@example.com is already registered"));

		assertThat(mvc.post()
			.uri("/api/users")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "alice@example.com", "name": "Alice", "role": "CUSTOMER"}
					"""))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Email alice@example.com is already registered");
	}

}
