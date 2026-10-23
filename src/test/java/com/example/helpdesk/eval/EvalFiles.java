package com.example.helpdesk.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Reads the eval data (src/test/resources/eval/*.json) into records, and writes reports.
public final class EvalFiles {

	private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

	private EvalFiles() {
	}

	public static <T> T read(String classpathFile, Class<T> type) {
		try (InputStream in = new ClassPathResource(classpathFile).getInputStream()) {
			return JSON.readValue(in, type);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Can't read " + classpathFile, ex);
		}
	}

	public static void write(Path file, Object value) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, JSON.writeValueAsString(value));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Can't write " + file, ex);
		}
	}

}
