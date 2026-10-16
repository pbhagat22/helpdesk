package com.example.helpdesk.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.helpdesk.common.ConflictException;

class AdminBootstrapTests {

	private final UserService userService = mock(UserService.class);

	private final AdminBootstrap bootstrap = new AdminBootstrap(
			new AdminProperties("admin@example.com", "admin-password"), userService);

	@Test
	void createsTheAdmin() {
		given(userService.createAdminIfMissing("admin@example.com", "admin-password")).willReturn(true);

		bootstrap.run(null);

		then(userService).should().createAdminIfMissing("admin@example.com", "admin-password");
	}

	// Two instances racing: the insert loses on the unique email constraint at commit...
	@Test
	void losingTheRaceAtCommitDoesNotStopStartup() {
		given(userService.createAdminIfMissing("admin@example.com", "admin-password"))
			.willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

		assertThatCode(() -> bootstrap.run(null)).doesNotThrowAnyException();
	}

	// ...or the other instance committed just before our email check.
	@Test
	void losingTheRaceAtTheEmailCheckDoesNotStopStartup() {
		given(userService.createAdminIfMissing("admin@example.com", "admin-password"))
			.willThrow(new ConflictException("Email admin@example.com is already registered"));

		assertThatCode(() -> bootstrap.run(null)).doesNotThrowAnyException();
	}

	@Test
	void doesNothingWithoutConfiguration() {
		new AdminBootstrap(new AdminProperties(null, null), userService).run(null);

		then(userService).shouldHaveNoInteractions();
	}

}
