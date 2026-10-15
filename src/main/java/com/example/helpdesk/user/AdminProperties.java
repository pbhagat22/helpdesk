package com.example.helpdesk.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds helpdesk.admin.email / helpdesk.admin.password from configuration. Spring's relaxed
// binding also reads them from the environment variables HELPDESK_ADMIN_EMAIL and
// HELPDESK_ADMIN_PASSWORD, which is how they're set (never commit a real password).
@ConfigurationProperties("helpdesk.admin")
public record AdminProperties(String email, String password) {
}
