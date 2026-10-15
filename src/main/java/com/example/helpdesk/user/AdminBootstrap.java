package com.example.helpdesk.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Solves the chicken-and-egg problem: only admins can create admins, so the first one is
// created at startup from configuration. Runs on every start, but only creates the user
// if that email doesn't exist yet (it never changes an existing user's password).
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminProperties admin;

	private final UserService userService;

	AdminBootstrap(AdminProperties admin, UserService userService) {
		this.admin = admin;
		this.userService = userService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
			log.info("No bootstrap admin configured (set HELPDESK_ADMIN_EMAIL and HELPDESK_ADMIN_PASSWORD)");
			return;
		}
		if (userService.createAdminIfMissing(admin.email(), admin.password())) {
			log.info("Created bootstrap admin {}", admin.email());
		}
	}

}
