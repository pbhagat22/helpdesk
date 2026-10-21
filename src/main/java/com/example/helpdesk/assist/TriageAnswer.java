package com.example.helpdesk.assist;

import com.example.helpdesk.ticket.Category;
import com.example.helpdesk.ticket.Priority;

// The model's triage answer (see TicketTriageService.SCHEMA). Jackson turns "HIGH" into
// Priority.HIGH; a value that isn't one of the enum's makes parsing fail.
record TriageAnswer(Priority priority, Category category, String reason) {
}
