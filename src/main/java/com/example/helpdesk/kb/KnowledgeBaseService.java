package com.example.helpdesk.kb;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.helpdesk.assist.AiCaller;
import com.example.helpdesk.assist.AiFeature;
import com.example.helpdesk.assist.EmbeddingClient;
import com.example.helpdesk.common.ServiceUnavailableException;
import com.example.helpdesk.common.TooManyRequestsException;
import com.example.helpdesk.security.HelpdeskUserDetails;

// Knowledge base operations that involve embeddings. Not @Transactional: embedding calls
// Bedrock (once per chunk), and no database connection should be held meanwhile. The database
// steps run in KbArticleService's and KbChunkStore's own transactions.
@Service
public class KnowledgeBaseService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

	// Nearest chunks fetched per search; several can belong to the same article
	private static final int CANDIDATE_CHUNKS = 20;

	// The nearest chunks are returned even when nothing is relevant, so weak matches are dropped.
	// Tuned with Titan V2 on the sample articles: the right article scored 0.30-0.48 for
	// paraphrased questions, and an unrelated question ("weather in Paris") at most 0.10.
	static final double MIN_SIMILARITY = 0.2;

	private final KbArticleService articles;

	private final KbChunkStore chunks;

	private final EmbeddingClient embeddings;

	public KnowledgeBaseService(KbArticleService articles, KbChunkStore chunks, EmbeddingClient embeddings) {
		this.articles = articles;
		this.chunks = chunks;
		this.embeddings = embeddings;
	}

	// Saving never fails because of Bedrock: if embedding fails, the article is saved anyway
	// (chunkCount tells whether it's searchable) and can be re-embedded later.
	// me: who writes it; the embedding tokens are counted against them.
	public ArticleResponse create(ArticleRequest request, HelpdeskUserDetails me) {
		Long id = articles.create(request, me.getId());
		indexQuietly(id, me);
		return articles.get(id);
	}

	// If re-embedding fails, the old chunks stay: slightly stale search beats none.
	public ArticleResponse update(Long id, ArticleRequest request, HelpdeskUserDetails me) {
		articles.update(id, request);
		indexQuietly(id, me);
		return articles.get(id);
	}

	// Splits the article into chunks, embeds each, and replaces its stored chunks. The title is
	// embedded with every chunk, so a chunk like "Click Save." still carries its topic.
	public void index(Long id, HelpdeskUserDetails me) {
		KbArticleService.ArticleText article = articles.text(id).orElse(null);
		if (article == null) {
			return;
		}
		AiCaller caller = AiCaller.user(AiFeature.KB_INDEX, me);
		List<KbChunkStore.EmbeddedChunk> embedded = TextChunker.split(article.body())
			.stream()
			.map(chunk -> new KbChunkStore.EmbeddedChunk(chunk,
					embeddings.embed(article.title() + "\n\n" + chunk, caller)))
			.toList();
		chunks.replace(id, embedded);
		log.info("Embedded article {} as {} chunks", id, embedded.size());
	}

	public int reindexAll(HelpdeskUserDetails me) {
		List<Long> ids = articles.ids();
		ids.forEach(id -> index(id, me));
		return ids.size();
	}

	// Semantic search: embed the question, find the closest chunks, keep the best chunk per
	// article (the list is ordered by distance, so the first one seen is the best).
	// me: who searches (directly, or through assist or the agent's tool).
	public List<ArticleSearchResult> search(String query, int limit, HelpdeskUserDetails me) {
		float[] queryVector = embeddings.embed(query, AiCaller.user(AiFeature.KB_SEARCH, me));
		Map<Long, KbChunkStore.Match> bestPerArticle = new LinkedHashMap<>();
		for (KbChunkStore.Match match : chunks.nearest(queryVector, CANDIDATE_CHUNKS)) {
			if (1 - match.distance() >= MIN_SIMILARITY) {
				bestPerArticle.putIfAbsent(match.articleId(), match);
			}
		}
		List<KbChunkStore.Match> top = bestPerArticle.values().stream().limit(limit).toList();
		Map<Long, String> titles = articles.titles(top.stream().map(KbChunkStore.Match::articleId).toList());
		return top.stream()
			.filter(match -> titles.containsKey(match.articleId())) // deleted meanwhile
			.map(match -> new ArticleSearchResult(match.articleId(), titles.get(match.articleId()), match.content(),
					1 - match.distance()))
			.toList();
	}

	private void indexQuietly(Long id, HelpdeskUserDetails me) {
		try {
			index(id, me);
		}
		catch (ServiceUnavailableException ex) {
			log.warn("Couldn't embed article {}; it isn't searchable until it's re-embedded", id, ex);
		}
		catch (TooManyRequestsException ex) {
			log.warn("Didn't embed article {} ({}); it isn't searchable until it's re-embedded", id, ex.getMessage());
		}
	}

}
