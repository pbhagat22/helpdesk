package com.example.helpdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.helpdesk.common.BadRequestException;
import com.example.helpdesk.common.NotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

	private static final Long ADMIN_ID = 1L;

	@Mock
	private UserRepository users;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	@Test
	void changeRolePromotesAnotherUser() {
		User carol = new User("carol@example.com", "Carol", Role.CUSTOMER, "{noop}unused");
		ReflectionTestUtils.setField(carol, "id", 5L);
		given(users.findById(5L)).willReturn(Optional.of(carol));

		UserResponse response = userService.changeRole(5L, Role.AGENT, ADMIN_ID);

		assertThat(response.role()).isEqualTo(Role.AGENT);
		assertThat(carol.getRole()).isEqualTo(Role.AGENT);
	}

	@Test
	void adminsCantChangeTheirOwnRole() {
		assertThatThrownBy(() -> userService.changeRole(ADMIN_ID, Role.CUSTOMER, ADMIN_ID))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("You can't change your own role");
		then(users).shouldHaveNoInteractions();
	}

	@Test
	void changeRoleFailsForUnknownUser() {
		given(users.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> userService.changeRole(99L, Role.AGENT, ADMIN_ID))
			.isInstanceOf(NotFoundException.class);
	}

}
