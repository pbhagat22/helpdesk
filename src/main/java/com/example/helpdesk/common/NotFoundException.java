package com.example.helpdesk.common;

public class NotFoundException extends RuntimeException {

	public NotFoundException(String entity, Long id) {
		super(entity + " " + id + " not found");
	}

}
