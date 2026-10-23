package com.example.helpdesk.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.helpdesk.assist.AiCaller;
import com.example.helpdesk.assist.AiFeature;
import com.example.helpdesk.assist.EmbeddingClient;
import com.example.helpdesk.common.ServiceUnavailableException;

class KnowledgeBaseServiceTests {

	private final KbArticleService articles = mock(KbArticleService.class);

	private final KbChunkStore chunks = mock(KbChunkStore.class);

	private final EmbeddingClient embeddings = mock(EmbeddingClient.class);

	private final KnowledgeBaseService service = new KnowledgeBaseService(articles, chunks, embeddings);

	private final float[] vector = new float[EmbeddingClient.DIMENSIONS];

	@Test
	void indexEmbedsEachChunkWithTheTitle() {
		given(articles.text(1L)).willReturn(Optional.of(new KbArticleService.ArticleText("Passwords", "Click reset.")));
		given(embeddings.embed(anyString(), any())).willReturn(vector);

		service.index(1L, 2L);

		then(embeddings).should().embed("Passwords\n\nClick reset.", AiCaller.user(AiFeature.KB_INDEX, 2L));
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<KbChunkStore.EmbeddedChunk>> stored = ArgumentCaptor.forClass(List.class);
		then(chunks).should().replace(eq(1L), stored.capture());
		// The stored text is the chunk alone; the title is only part of what gets embedded
		assertThat(stored.getValue()).extracting(KbChunkStore.EmbeddedChunk::content).containsExactly("Click reset.");
	}

	@Test
	void createSavesTheArticleEvenIfEmbeddingFails() {
		ArticleRequest request = new ArticleRequest("Passwords", "Click reset.");
		given(articles.create(request, 2L)).willReturn(1L);
		given(articles.text(1L)).willReturn(Optional.of(new KbArticleService.ArticleText("Passwords", "Click reset.")));
		given(embeddings.embed(anyString(), any())).willThrow(new ServiceUnavailableException("down", null));

		service.create(request, 2L);

		then(chunks).should(never()).replace(anyLong(), any());
		then(articles).should().get(1L);
	}

	@Test
	void searchKeepsTheBestChunkPerArticleAndDropsWeakMatches() {
		given(embeddings.embed("forgot password", AiCaller.user(AiFeature.KB_SEARCH, 2L))).willReturn(vector);
		given(chunks.nearest(eq(vector), anyInt())).willReturn(List.of(new KbChunkStore.Match(1, 0, "best", 0.5),
				new KbChunkStore.Match(1, 1, "second best of article 1", 0.6),
				new KbChunkStore.Match(2, 0, "billing", 0.7), new KbChunkStore.Match(3, 0, "weather", 0.95)));
		given(articles.titles(List.of(1L, 2L))).willReturn(Map.of(1L, "Passwords", 2L, "Billing"));

		List<ArticleSearchResult> results = service.search("forgot password", 5, 2L);

		assertThat(results).containsExactly(new ArticleSearchResult(1L, "Passwords", "best", 0.5),
				new ArticleSearchResult(2L, "Billing", "billing", 1 - 0.7));
	}

}
