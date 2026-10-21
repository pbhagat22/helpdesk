package com.example.helpdesk.kb;

// A semantic search hit: the article, its best-matching chunk, and how similar that chunk is to
// the question (cosine similarity: 1 = same meaning, around 0 = unrelated).
public record ArticleSearchResult(Long id, String title, String snippet, double similarity) {
}
