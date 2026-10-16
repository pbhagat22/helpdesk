package com.example.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

// Same annotations as HelpdeskFlowTests on purpose: Spring caches test application contexts by
// their configuration, so identical setups share one context (and one set of containers)
// instead of starting Postgres, Kafka and Elasticsearch twice.
@SpringBootTest(properties = { "helpdesk.admin.email=admin@example.com", "helpdesk.admin.password=admin-password" })
@Import({ TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class,
		ElasticsearchTestcontainersConfiguration.class })
@AutoConfigureMockMvc
class HelpdeskApplicationTests {

	@Test
	void contextLoads() {
	}

}
