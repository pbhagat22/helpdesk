package com.example.helpdesk.assist;

import java.util.List;

// What the assist endpoint returns to the UI: the model's summary and draft reply, plus the
// knowledge base articles the reply is based on (only ones that were really in the prompt).
public record AssistResponse(String summary, String suggestedReply, List<Source> sources) {

	public record Source(Long id, String title) {
	}

}
