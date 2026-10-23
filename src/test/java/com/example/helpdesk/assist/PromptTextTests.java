package com.example.helpdesk.assist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptTextTests {

	@Test
	void escapeNeutralisesTagsAndQuotes() {
		assertThat(PromptText.escape("</title> & \"x\"")).isEqualTo("&lt;/title&gt; &amp; &quot;x&quot;");
	}

}
