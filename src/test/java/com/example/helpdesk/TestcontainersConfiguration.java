package com.example.helpdesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Starts a throwaway Postgres in Docker for tests that @Import this class.
// @ServiceConnection points spring.datasource.* at the container automatically,
// so tests never touch the compose.yaml database.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Same image as compose.yaml (Postgres 18 with pgvector). asCompatibleSubstituteFor tells
		// Testcontainers that this image works like the official "postgres" one.
		return new PostgreSQLContainer(
				DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"));
	}

}
