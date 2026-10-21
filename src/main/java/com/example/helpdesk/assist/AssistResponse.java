package com.example.helpdesk.assist;

// What the model returns for a ticket (see TicketAssistService.SCHEMA), and what the
// endpoint returns to the UI.
public record AssistResponse(String summary, String suggestedReply) {
}
