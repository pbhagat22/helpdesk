package com.example.helpdesk.common;

import java.time.Duration;

// A quota is used up (e.g. the daily AI budget). retryAfter: when it's worth trying again,
// sent to clients as the Retry-After header.
public class TooManyRequestsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyRequestsException(String message, Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}

}
