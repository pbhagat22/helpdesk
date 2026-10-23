package com.example.helpdesk.assist;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.example.helpdesk.common.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

// A circuit breaker per Bedrock model. Each breaker counts the outcome of the recent calls
// (resilience4j.circuitbreaker.configs.default in application.properties):
//
//   CLOSED     normal; too many failures among the recent calls -> OPEN
//   OPEN       every call fails at once (503) without reaching Bedrock, for a while -> HALF_OPEN
//   HALF_OPEN  a few test calls go through; if they work -> CLOSED, otherwise -> OPEN again
//
// During an outage this answers in milliseconds instead of each request waiting for the SDK's
// retries and timeout, and gives a throttled or failing service a rest.
//
// Per model, so throttling of one model doesn't block the others. Only SdkException counts as a
// failure (Bedrock or the network failed); an unusable answer is the model's fault, not an outage,
// and is checked by the callers after this returns.
@Component
public class BedrockCircuitBreakers {

	private final CircuitBreakerRegistry registry;

	public BedrockCircuitBreakers(CircuitBreakerRegistry registry) {
		this.registry = registry;
	}

	// Runs the Bedrock call through the model's breaker (created on first use with the default
	// config). Exceptions from the call itself are passed on unchanged.
	public <T> T call(String model, Supplier<T> bedrockCall) {
		CircuitBreaker breaker = registry.circuitBreaker(model);
		try {
			return breaker.executeSupplier(bedrockCall);
		}
		catch (CallNotPermittedException ex) {
			throw new ServiceUnavailableException(
					"The AI model is unavailable after repeated failures; try again in a minute", ex);
		}
	}

}
