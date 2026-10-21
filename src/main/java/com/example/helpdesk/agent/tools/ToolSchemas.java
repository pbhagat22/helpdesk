package com.example.helpdesk.agent.tools;

import java.util.Arrays;
import java.util.stream.Collectors;

final class ToolSchemas {

	private ToolSchemas() {
	}

	// "LOW", "MEDIUM", ... for a JSON schema "enum", taken from the Java enum so they can't drift
	static String enumValues(Enum<?>[] values) {
		return Arrays.stream(values).map(value -> "\"" + value.name() + "\"").collect(Collectors.joining(", "));
	}

}
