package com.example.helpdesk.agent.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.helpdesk.agent.AgentTool;
import com.example.helpdesk.search.TicketSearchService;
import com.example.helpdesk.security.HelpdeskUserDetails;

@Component
class SearchTicketsTool implements AgentTool<SearchTicketsTool.Input> {

	static final int LIMIT = 10;

	record Input(String query) {
	}

	// matches: snippets of the text that matched, so the model can tell why a ticket was found
	record Hit(TicketSummary ticket, List<String> matches) {
	}

	private final TicketSearchService searchService;

	SearchTicketsTool(TicketSearchService searchService) {
		this.searchService = searchService;
	}

	@Override
	public String name() {
		return "search_tickets";
	}

	@Override
	public String description() {
		return "Full-text search over ticket titles, descriptions and comments (tolerates typos and word "
				+ "forms). Use it to find tickets about a topic, e.g. \"printer\" or \"invoice missing\". Returns "
				+ "the best " + LIMIT + " matches, best first.";
	}

	@Override
	public String inputSchema() {
		return """
				{
				  "type": "object",
				  "properties": {
				    "query": {"type": "string", "description": "A few keywords"}
				  },
				  "required": ["query"]
				}""";
	}

	@Override
	public Class<Input> inputType() {
		return Input.class;
	}

	@Override
	public List<Hit> run(Input input, HelpdeskUserDetails me) {
		if (input.query() == null || input.query().isBlank()) {
			throw new IllegalArgumentException("query must not be empty");
		}
		return searchService.search(input.query(), me)
			.stream()
			.limit(LIMIT)
			.map(result -> new Hit(TicketSummary.from(result.ticket()),
					// The highlight markers are for the UI; the model only needs the words
					result.highlights()
						.stream()
						.map(text -> text.replace(TicketSearchService.MARK_START, "")
							.replace(TicketSearchService.MARK_END, ""))
						.toList()))
			.toList();
	}

}
