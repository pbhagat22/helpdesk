package com.example.helpdesk.kb;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.helpdesk.common.NotFoundException;
import com.example.helpdesk.user.User;
import com.example.helpdesk.user.UserRepository;

// The articles themselves (database only). Embedding happens in KnowledgeBaseService, outside
// these transactions.
@Service
@Transactional(readOnly = true)
public class KbArticleService {

	private final KbArticleRepository articles;

	private final KbChunkStore chunks;

	private final UserRepository users;

	public KbArticleService(KbArticleRepository articles, KbChunkStore chunks, UserRepository users) {
		this.articles = articles;
		this.chunks = chunks;
		this.users = users;
	}

	// The text an article's chunks are made from
	record ArticleText(String title, String body) {
	}

	public List<ArticleResponse> list() {
		Map<Long, Integer> counts = chunks.counts();
		return articles.findAllByOrderByTitleAsc()
			.stream()
			.map(article -> ArticleResponse.from(article, counts.getOrDefault(article.getId(), 0)))
			.toList();
	}

	public ArticleResponse get(Long id) {
		return ArticleResponse.from(find(id), chunks.count(id));
	}

	public List<Long> ids() {
		return articles.findAll().stream().map(KbArticle::getId).toList();
	}

	public Map<Long, String> titles(List<Long> ids) {
		return articles.findAllById(ids)
			.stream()
			.collect(java.util.stream.Collectors.toMap(KbArticle::getId, KbArticle::getTitle));
	}

	Optional<ArticleText> text(Long id) {
		return articles.findById(id).map(article -> new ArticleText(article.getTitle(), article.getBody()));
	}

	@Transactional
	public Long create(ArticleRequest request, Long authorId) {
		User author = users.findById(authorId).orElseThrow(() -> new NotFoundException("User", authorId));
		return articles.save(new KbArticle(request.title(), request.body(), author)).getId();
	}

	@Transactional
	public void update(Long id, ArticleRequest request) {
		KbArticle article = find(id);
		article.setTitle(request.title());
		article.setBody(request.body());
	}

	// The chunks go too: the foreign key has "on delete cascade"
	@Transactional
	public void delete(Long id) {
		articles.delete(find(id));
	}

	private KbArticle find(Long id) {
		return articles.findById(id).orElseThrow(() -> new NotFoundException("Article", id));
	}

}
