package com.example.helpdesk.search;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class SearchController {

	private final TicketSearchService searchService;

	public SearchController(TicketSearchService searchService) {
		this.searchService = searchService;
	}

	// GET /api/tickets/search?q=printer+smoke  (customers only get their own tickets)
	// Spring validates the @NotBlank/@Size constraints on the parameter: a blank q is a 400.
	@GetMapping("/api/tickets/search")
	public List<TicketSearchResult> search(@RequestParam @NotBlank @Size(max = 200) String q,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return searchService.search(q, me);
	}

	// Rebuilds the whole index from Postgres.
	@PostMapping("/api/admin/search/reindex")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Integer> reindex() {
		return Map.of("indexed", searchService.reindexAll());
	}

}
