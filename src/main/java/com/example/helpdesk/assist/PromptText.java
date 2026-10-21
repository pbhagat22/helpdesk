package com.example.helpdesk.assist;

// Helpers for putting user-written text into prompts.
final class PromptText {

	private PromptText() {
	}

	// Prompts wrap ticket text in XML-style tags so the model sees where each piece starts and
	// ends. Escaping means a customer can't close a tag and "step outside" their own text.
	static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
