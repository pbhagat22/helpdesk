package com.example.helpdesk.search;

import java.util.List;

import com.example.helpdesk.ticket.TicketResponse;

// highlights: snippets of matching text, with each matched word wrapped in MARK_START and
// MARK_END (see TicketSearchService). Plain text otherwise: the client must render them as
// text, never as HTML, because they contain whatever users typed into tickets.
public record TicketSearchResult(TicketResponse ticket, List<String> highlights) {
}
