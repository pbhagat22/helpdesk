package com.example.helpdesk.common;

// The request is well-formed, but breaks a business rule.
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
