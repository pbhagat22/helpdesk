package com.example.helpdesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

// A throwaway Elasticsearch for tests that run the whole application (@SpringBootTest).
@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	ElasticsearchContainer elasticsearchContainer() {
		// Same image as compose.yaml; security off and a small heap, as in local development
		return new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.5")
			.withEnv("xpack.security.enabled", "false")
			.withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");
	}

}
