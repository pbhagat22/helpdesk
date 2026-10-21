package com.example.helpdesk.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextChunkerTests {

	@Test
	void shortTextIsOneChunk() {
		assertThat(TextChunker.split("  First paragraph.\n\nSecond paragraph.  "))
			.containsExactly("First paragraph.\n\nSecond paragraph.");
	}

	@Test
	void paragraphsArePackedIntoChunksWithoutBeingSplit() {
		String paragraph = "x".repeat(600);
		List<String> chunks = TextChunker.split(paragraph + "\n\n" + paragraph + "\n \n" + "short");

		// 600 + 2 + 600 would be over 1000, so the second paragraph starts a new chunk
		assertThat(chunks).containsExactly(paragraph, paragraph + "\n\nshort");
	}

	@Test
	void longParagraphIsSplitBetweenSentences() {
		String sentence = "y".repeat(399) + ".";
		String paragraph = sentence + " " + sentence + " " + sentence;

		List<String> chunks = TextChunker.split(paragraph);

		assertThat(chunks).containsExactly(sentence + " " + sentence, sentence);
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(TextChunker.MAX_CHARS));
	}

	@Test
	void textWithoutSentencesIsCutHard() {
		List<String> chunks = TextChunker.split("z".repeat(2500));

		assertThat(chunks).extracting(String::length).containsExactly(1000, 1000, 500);
	}

}
