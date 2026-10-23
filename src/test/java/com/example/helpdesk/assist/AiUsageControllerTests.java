package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.security.SecurityConfig;
import com.example.helpdesk.user.Role;

@WebMvcTest(AiUsageController.class)
@Import(SecurityConfig.class)
class AiUsageControllerTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	private static final HelpdeskUserDetails ada = new HelpdeskUserDetails(3L, "ada@example.com", "unused",
			Role.ADMIN);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AiUsageService usage;

	@Test
	void adminsSeeTheReportForSevenDaysByDefault() {
		given(usage.report(7)).willReturn(new AiUsageReport(Instant.parse("2026-10-16T00:00:00Z"),
				new BigDecimal("0.0042"), List.of(), List.of(), List.of()));

		assertThat(mvc.get().uri("/api/admin/ai/usage").with(user(ada))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.estimatedCostUsd")
			.isEqualTo(0.0042);
	}

	@Test
	void agentsCannotSeeTheReport() {
		assertThat(mvc.get().uri("/api/admin/ai/usage").with(user(bob))).hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(usage);
	}

	@Test
	void daysMustBeBetween1And90() {
		assertThat(mvc.get().uri("/api/admin/ai/usage?days=0").with(user(ada))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/admin/ai/usage?days=91").with(user(ada))).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
