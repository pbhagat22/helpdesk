package com.example.helpdesk.agent.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.helpdesk.agent.AgentTool;
import com.example.helpdesk.kb.KnowledgeBaseService;
import com.example.helpdesk.security.HelpdeskUserDetails;

// Lets the agent look things up in the help articles. Unlike search_tickets (keywords, via
// Elasticsearch), this searches by meaning (embeddings, via pgvector), so a whole question
// works as the query. Everyone logged in can read the knowledge base, so there's no
// per-user filtering here.
@Component
class SearchKnowledgeBaseTool implements AgentTool<SearchKnowledgeBaseTool.Input> {

	static final int LIMIT = 3;

	record Input(String question) {
	}

	// excerpt: the best-matching part of the article (up to about 1000 characters), which is
	// usually enough to answer; the similarity score is left out, it means nothing to the model
	record Hit(Long articleId, String title, String excerpt) {
	}

	private final KnowledgeBaseService knowledgeBase;

	SearchKnowledgeBaseTool(KnowledgeBaseService knowledgeBase) {
		this.knowledgeBase = knowledgeBase;
	}

	@Override
	public String name() {
		return "search_knowledge_base";
	}

	@Override
	public String description() {
		return "Searches the company's help articles by meaning, for how-to questions and policies (e.g. refunds, "
				+ "passwords, VPN). Returns up to " + LIMIT + " relevant articles, best first, each with an "
				+ "excerpt; an empty list means no article covers it.";
	}

	@Override
	public String inputSchema() {
		return """
				{
				  "type": "object",
				  "properties": {
				    "question": {"type": "string", "description": "The question or topic, in plain words"}
				  },
				  "required": ["question"]
				}""";
	}

	@Override
	public Class<Input> inputType() {
		return Input.class;
	}

	@Override
	public List<Hit> run(Input input, HelpdeskUserDetails me) {
		if (input.question() == null || input.question().isBlank()) {
			throw new IllegalArgumentException("question must not be empty");
		}
		return knowledgeBase.search(input.question(), LIMIT, me)
			.stream()
			.map(result -> new Hit(result.id(), result.title(), result.snippet()))
			.toList();
	}

}
