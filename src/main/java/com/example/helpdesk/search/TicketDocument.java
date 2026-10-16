package com.example.helpdesk.search;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

// A ticket as stored in the Elasticsearch index "tickets": a denormalized copy for searching.
// Postgres stays the source of truth; this copy can always be rebuilt from it (see reindex).
//
// Text fields use the "english" analyzer: it lowercases, drops filler words ("the", "is") and
// reduces words to their stem, so "printing", "printed" and "prints" all match (stem "print"),
// and "smoking" matches "smoke". It treats "printer" as a word of its own, though; the search
// query's prefix matching is what lets "print" find "printer" (see TicketSearchService).
// Keyword fields are stored as-is, for exact filtering. createIndex = false: the app creates
// the index itself (TicketSearchService.ensureIndex), so a missing Elasticsearch can't stop
// the app from starting.
@Document(indexName = "tickets", createIndex = false)
public record TicketDocument(
		@Id Long id,
		@Field(type = FieldType.Text, analyzer = "english") String title,
		@Field(type = FieldType.Text, analyzer = "english") String description,
		@Field(type = FieldType.Text, analyzer = "english") List<String> comments,
		@Field(type = FieldType.Keyword) String status,
		@Field(type = FieldType.Keyword) String priority,
		@Field(type = FieldType.Long) Long reporterId) {
}
