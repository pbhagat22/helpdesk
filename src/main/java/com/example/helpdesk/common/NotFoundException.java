package com.example.helpdesk.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ResponseStatus makes Spring answer 404 when this escapes a controller.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

	public NotFoundException(String entity, Long id) {
		super(entity + " " + id + " not found");
	}

}
