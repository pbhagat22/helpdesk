package com.example.helpdesk.assist;

// What a Bedrock call was made for, as recorded in the usage ledger.
public enum AiFeature {

	// Summary + suggested reply for a ticket (staff)
	ASSIST,

	// Priority/category suggestion for a new ticket (background, no user)
	TRIAGE,

	// One model call of the agent's tool loop (staff)
	AGENT,

	// Embedding a search query: KB search, suggested articles, assist and the agent's KB tool
	KB_SEARCH,

	// Embedding article chunks when an article is saved or the KB is reindexed
	KB_INDEX

}
