package com.example.helpdesk.kb;

import java.util.ArrayList;
import java.util.List;

// Splits an article into chunks of up to MAX_CHARS, keeping paragraphs (and if needed
// sentences) whole.
//
// Why chunk at all: one embedding for a long article blurs all its topics together, so a
// question about one section matches it weakly. Small chunks give precise matches, and in 9c.2
// only the relevant chunks need to go into the model's prompt.
final class TextChunker {

	static final int MAX_CHARS = 1000;

	private TextChunker() {
	}

	static List<String> split(String text) {
		List<String> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		// Paragraphs are separated by blank lines
		for (String paragraph : text.strip().split("\\n\\s*\\n")) {
			for (String piece : fit(paragraph.strip())) {
				if (!current.isEmpty() && current.length() + 2 + piece.length() > MAX_CHARS) {
					chunks.add(current.toString());
					current.setLength(0);
				}
				if (!current.isEmpty()) {
					current.append("\n\n");
				}
				current.append(piece);
			}
		}
		if (!current.isEmpty()) {
			chunks.add(current.toString());
		}
		return chunks;
	}

	// A paragraph that's too long is split between sentences; a "sentence" that's still too
	// long is cut hard.
	private static List<String> fit(String paragraph) {
		if (paragraph.length() <= MAX_CHARS) {
			return List.of(paragraph);
		}
		List<String> pieces = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
			if (!current.isEmpty() && current.length() + 1 + sentence.length() > MAX_CHARS) {
				pieces.add(current.toString());
				current.setLength(0);
			}
			while (sentence.length() > MAX_CHARS) {
				pieces.add(sentence.substring(0, MAX_CHARS));
				sentence = sentence.substring(MAX_CHARS);
			}
			if (!current.isEmpty()) {
				current.append(' ');
			}
			current.append(sentence);
		}
		if (!current.isEmpty()) {
			pieces.add(current.toString());
		}
		return pieces;
	}

}
