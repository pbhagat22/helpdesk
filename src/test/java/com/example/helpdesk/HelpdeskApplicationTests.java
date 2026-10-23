package com.example.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import software.amazon.awssdk.core.exception.SdkClientException;

// Same annotations as HelpdeskFlowTests on purpose: Spring caches test application contexts by
// their configuration, so identical setups share one context (and one set of containers)
// instead of starting Postgres, Kafka and Elasticsearch twice.
@SpringBootTest(properties = { "helpdesk.admin.email=admin@example.com", "helpdesk.admin.password=admin-password",
		"spring.ai.mcp.client.enabled=false" })
@Import({ TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class,
		ElasticsearchTestcontainersConfiguration.class, FakeBedrockConfiguration.class })
@AutoConfigureMockMvc
class HelpdeskApplicationTests {

	@Test
	void contextLoads() {
	}

	// BedrockCircuitBreakers creates a breaker per model on first use, from the registry's
	// default config. If resilience4j.circuitbreaker.configs.default weren't applied, they'd
	// silently get Resilience4j's own defaults (100 calls before they can open).
	@Test
	void circuitBreakersUseOurDefaultConfig(@Autowired CircuitBreakerRegistry registry) {
		CircuitBreakerConfig config = registry.circuitBreaker("any-model").getCircuitBreakerConfig();
		assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
		assertThat(config.getSlidingWindowSize()).isEqualTo(10);
		assertThat(config.getRecordExceptionPredicate().test(SdkClientException.create("down"))).isTrue();
		assertThat(config.getRecordExceptionPredicate().test(new IllegalStateException())).isFalse();
	}

	@Test
	void agentBulkheadUsesOurConfig(@Autowired BulkheadRegistry registry) {
		BulkheadConfig config = registry.bulkhead("agent").getBulkheadConfig();
		assertThat(config.getMaxConcurrentCalls()).isEqualTo(4);
		assertThat(config.getMaxWaitDuration()).isZero();
	}

}
