package com.example.helpdesk.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.helpdesk.security.HelpdeskUserDetails;
import com.example.helpdesk.security.SecurityConfig;
import com.example.helpdesk.user.Role;

@WebMvcTest(KbController.class)
@Import(SecurityConfig.class)
class KbControllerTests {

	private static final HelpdeskUserDetails alice = new HelpdeskUserDetails(1L, "alice@example.com", "unused",
			Role.CUSTOMER);

	private static final HelpdeskUserDetails bob = new HelpdeskUserDetails(2L, "bob@example.com", "unused",
			Role.AGENT);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private KbArticleService articles;

	@MockitoBean
	private KnowledgeBaseService knowledgeBase;

	@Test
	void customersCanSearch() {
		given(knowledgeBase.search("forgot password", 5))
			.willReturn(List.of(new ArticleSearchResult(1L, "Passwords", "Click reset.", 0.4)));

		assertThat(mvc.get().uri("/api/kb/search?q=forgot password").with(user(alice))).hasStatusOk()
			.bodyJson()
			.extractingPath("$[0].title")
			.isEqualTo("Passwords");
	}

	@Test
	void customersCannotWriteArticles() {
		assertThat(mvc.post().uri("/api/kb/articles").with(user(alice)).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Free refunds", "body": "Ask for one!"}
				"""))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.delete().uri("/api/kb/articles/1").with(user(alice))).hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(knowledgeBase);
	}

	@Test
	void agentCreatesArticle() {
		given(knowledgeBase.create(any(), any()))
			.willReturn(new ArticleResponse(1L, "Passwords", "Click reset.", null, null, null, 1));

		assertThat(mvc.post().uri("/api/kb/articles").with(user(bob)).contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "Passwords", "body": "Click reset."}
				"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.chunkCount")
			.isEqualTo(1);
	}

	@Test
	void articleNeedsTitleAndBody() {
		assertThat(mvc.post().uri("/api/kb/articles").with(user(bob)).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asMap()
			.containsOnlyKeys("title", "body");
	}

	@Test
	void onlyAdminsReindex() {
		assertThat(mvc.post().uri("/api/admin/kb/reindex").with(user(bob))).hasStatus(HttpStatus.FORBIDDEN);
	}

}
