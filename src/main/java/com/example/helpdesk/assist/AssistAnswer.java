package com.example.helpdesk.assist;

import java.util.List;

// What the model returns for a ticket (see TicketAssistService.SCHEMA). sourceArticleIds are
// the knowledge base articles the model says it used; they're checked before reaching the UI.
public record AssistAnswer(String summary, String suggestedReply, List<Long> sourceArticleIds) {
}
