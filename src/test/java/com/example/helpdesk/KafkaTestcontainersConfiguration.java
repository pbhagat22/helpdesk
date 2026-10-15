package com.example.helpdesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

// A throwaway Kafka broker for tests that run the whole application (@SpringBootTest).
// Kept separate from TestcontainersConfiguration (Postgres) so that tests which only need
// the database, like @DataJpaTest, don't pay a few seconds to start Kafka too.
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		// Same image as compose.yaml
		return new KafkaContainer("apache/kafka:4.2.1");
	}

}
