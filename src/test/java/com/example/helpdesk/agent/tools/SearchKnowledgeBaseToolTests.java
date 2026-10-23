package com.example.helpdesk.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.helpdesk.kb.ArticleSearchResult;
import com.example.helpdesk.kb.KnowledgeBaseService;
import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.user.Role;

class SearchKnowledgeBaseToolTests {

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	private final KnowledgeBaseService knowledgeBase = mock(KnowledgeBaseService.class);

	private final SearchKnowledgeBaseTool tool = new SearchKnowledgeBaseTool(knowledgeBase);

	@Test
	void returnsArticlesWithExcerptsWithoutTheScore() {
		given(knowledgeBase.search("double charge", SearchKnowledgeBaseTool.LIMIT, bob.getId())).willReturn(
				List.of(new ArticleSearchResult(4L, "Refunds and duplicate charges", "Refunded in 5 to 10 days.", 0.4)));

		assertThat(tool.run(new SearchKnowledgeBaseTool.Input("double charge"), bob)).containsExactly(
				new SearchKnowledgeBaseTool.Hit(4L, "Refunds and duplicate charges", "Refunded in 5 to 10 days."));
	}

	@Test
	void emptyQuestionIsAnError() {
		assertThatThrownBy(() -> tool.run(new SearchKnowledgeBaseTool.Input(" "), bob))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
