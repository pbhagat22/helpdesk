package com.example.helpdesk.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.UserResponse;
import com.example.helpdesk.user.UserService;

class FindUsersToolTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	private final UserService userService = mock(UserService.class);

	private final FindUsersTool tool = new FindUsersTool(userService);

	@Test
	void matchesPartOfNameOrEmailIgnoringCaseAndFiltersByRole() {
		given(userService.list()).willReturn(List.of(
				new UserResponse(1L, "tess@example.com", "Tess Smith", Role.CUSTOMER, Instant.now()),
				new UserResponse(2L, "t.jones@example.com", "Theo Jones", Role.AGENT, Instant.now()),
				new UserResponse(3L, "carol@example.com", "Carol", Role.CUSTOMER, Instant.now())));

		assertThat(tool.run(new FindUsersTool.Input("TESS", null), bob)).extracting(FindUsersTool.UserInfo::id)
			.containsExactly(1L);
		assertThat(tool.run(new FindUsersTool.Input("jones@", null), bob)).extracting(FindUsersTool.UserInfo::id)
			.containsExactly(2L);
		assertThat(tool.run(new FindUsersTool.Input(null, Role.CUSTOMER), bob)).extracting(FindUsersTool.UserInfo::id)
			.containsExactly(1L, 3L);
	}

}
