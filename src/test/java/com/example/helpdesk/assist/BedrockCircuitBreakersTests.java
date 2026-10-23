package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.helpdesk.common.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

class BedrockCircuitBreakersTests {

	// The same settings as application.properties
	private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
		.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
		.slidingWindowSize(10)
		.minimumNumberOfCalls(5)
		.failureRateThreshold(50)
		.waitDurationInOpenState(Duration.ofSeconds(30))
		.permittedNumberOfCallsInHalfOpenState(2)
		.recordExceptions(SdkException.class)
		.build());

	private final BedrockCircuitBreakers breakers = new BedrockCircuitBreakers(registry);

	private final AtomicInteger bedrockCalls = new AtomicInteger();

	private String throttled() {
		bedrockCalls.incrementAndGet();
		throw ThrottlingException.builder().message("Too many requests").build();
	}

	@Test
	void opensAfterRepeatedFailuresAndThenFailsFastWithoutCallingBedrock() {
		for (int i = 0; i < 5; i++) {
			// The SDK's own exception is passed on while the breaker is closed
			assertThatThrownBy(() -> breakers.call("model-a", this::throttled)).isInstanceOf(ThrottlingException.class);
		}
		assertThat(registry.circuitBreaker("model-a").getState()).isEqualTo(CircuitBreaker.State.OPEN);

		assertThatThrownBy(() -> breakers.call("model-a", this::throttled))
			.isInstanceOf(ServiceUnavailableException.class)
			.hasMessageContaining("try again in a minute");
		assertThat(bedrockCalls).hasValue(5);
	}

	@Test
	void eachModelHasItsOwnBreaker() {
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> breakers.call("model-a", this::throttled));
		}

		assertThat(breakers.call("model-b", () -> "fine")).isEqualTo("fine");
	}

	// An unusable answer is checked after the call returns, so the breaker only ever sees
	// Bedrock's own failures; anything else thrown inside wouldn't count either.
	@Test
	void otherExceptionsDontCount() {
		for (int i = 0; i < 10; i++) {
			assertThatThrownBy(() -> breakers.call("model-a", () -> {
				throw new IllegalStateException("not an outage");
			}));
		}

		assertThat(registry.circuitBreaker("model-a").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

}
