package com.example.helpdesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Starts a throwaway Postgres in Docker for tests that @Import this class.
// @ServiceConnection points spring.datasource.* at the container automatically,
// so tests never touch the compose.yaml database.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Same image as compose.yaml, so tests run against the same Postgres version.
		return new PostgreSQLContainer("postgres:18");
	}

}
