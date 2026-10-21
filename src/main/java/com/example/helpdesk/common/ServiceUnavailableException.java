package com.example.helpdesk.common;

// A dependency the request needs (e.g. Elasticsearch, or Claude on Bedrock) is down. The rest of the app still works.
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
