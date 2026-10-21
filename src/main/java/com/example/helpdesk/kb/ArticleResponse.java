package com.example.helpdesk.kb;

import java.time.Instant;

import com.example.helpdesk.user.UserResponse;

// chunkCount: how many embedded chunks the article has. 0 means it isn't searchable yet
// (embedding failed, e.g. Bedrock was down); POST /api/admin/kb/reindex fixes that.
public record ArticleResponse(Long id, String title, String body, UserResponse author, Instant createdAt,
		Instant updatedAt, int chunkCount) {

	static ArticleResponse from(KbArticle article, int chunkCount) {
		return new ArticleResponse(article.getId(), article.getTitle(), article.getBody(),
				UserResponse.from(article.getAuthor()), article.getCreatedAt(), article.getUpdatedAt(), chunkCount);
	}

}
