package com.example.helpdesk.kb;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.helpdesk.security.HelpdeskUserDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Everyone who is logged in can read and search the knowledge base (it's help for customers
// too); only staff write it.
@RestController
public class KbController {

	private static final int SEARCH_RESULTS = 5;

	private final KbArticleService articles;

	private final KnowledgeBaseService knowledgeBase;

	public KbController(KbArticleService articles, KnowledgeBaseService knowledgeBase) {
		this.articles = articles;
		this.knowledgeBase = knowledgeBase;
	}

	@GetMapping("/api/kb/articles")
	public List<ArticleResponse> list() {
		return articles.list();
	}

	@GetMapping("/api/kb/articles/{id}")
	public ArticleResponse get(@PathVariable Long id) {
		return articles.get(id);
	}

	@PostMapping("/api/kb/articles")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public ArticleResponse create(@Valid @RequestBody ArticleRequest request,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return knowledgeBase.create(request, me);
	}

	@PutMapping("/api/kb/articles/{id}")
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public ArticleResponse update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return knowledgeBase.update(id, request, me);
	}

	@DeleteMapping("/api/kb/articles/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
	public void delete(@PathVariable Long id) {
		articles.delete(id);
	}

	// GET /api/kb/search?q=can't+sign+in : by meaning, not keywords
	@GetMapping("/api/kb/search")
	public List<ArticleSearchResult> search(@RequestParam @NotBlank @Size(max = 500) String q,
			@AuthenticationPrincipal HelpdeskUserDetails me) {
		return knowledgeBase.search(q, SEARCH_RESULTS, me);
	}

	// Re-embeds every article, e.g. after changing the embedding model or a Bedrock outage.
	@PostMapping("/api/admin/kb/reindex")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Integer> reindex(@AuthenticationPrincipal HelpdeskUserDetails me) {
		return Map.of("articles", knowledgeBase.reindexAll(me));
	}

}
