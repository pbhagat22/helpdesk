package com.example.helpdesk.kb;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// The kb_chunk table, with plain SQL through Spring's JdbcClient. pgvector takes a vector as
// text like '[0.12,-0.5,...]', cast to the vector type.
@Repository
public class KbChunkStore {

	// A chunk's text and its embedding, ready to store
	public record EmbeddedChunk(String content, float[] embedding) {
	}

	// A search hit: distance is the cosine distance, 0 = same direction (same meaning),
	// 1 = unrelated, 2 = opposite. Similarity = 1 - distance.
	public record Match(long articleId, int chunkIndex, String content, double distance) {
	}

	private final JdbcClient jdbc;

	public KbChunkStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// Replaces all of an article's chunks in one transaction, so a search never sees half of
	// the old chunks and half of the new ones.
	@Transactional
	public void replace(long articleId, List<EmbeddedChunk> chunks) {
		jdbc.sql("delete from kb_chunk where article_id = ?").param(articleId).update();
		for (int i = 0; i < chunks.size(); i++) {
			jdbc.sql("""
					insert into kb_chunk (article_id, chunk_index, content, embedding)
					values (?, ?, ?, cast(? as vector))
					""")
				.params(articleId, i, chunks.get(i).content(), toVectorLiteral(chunks.get(i).embedding()))
				.update();
		}
	}

	// The chunks closest in meaning to the query vector, closest first. "order by distance
	// limit n" is the shape the HNSW index can answer without comparing every row.
	@Transactional(readOnly = true)
	public List<Match> nearest(float[] query, int limit) {
		return jdbc.sql("""
				select article_id, chunk_index, content, embedding <=> cast(:query as vector) as distance
				from kb_chunk
				order by embedding <=> cast(:query as vector)
				limit :limit
				""")
			.param("query", toVectorLiteral(query))
			.param("limit", limit)
			.query((rs, row) -> new Match(rs.getLong("article_id"), rs.getInt("chunk_index"), rs.getString("content"),
					rs.getDouble("distance")))
			.list();
	}

	@Transactional(readOnly = true)
	public int count(long articleId) {
		return jdbc.sql("select count(*) from kb_chunk where article_id = ?").param(articleId).query(Integer.class).single();
	}

	// Chunk count per article, for the article list (articles with none aren't in the map)
	@Transactional(readOnly = true)
	public Map<Long, Integer> counts() {
		return jdbc.sql("select article_id, count(*) as chunks from kb_chunk group by article_id")
			.query((rs, row) -> Map.entry(rs.getLong("article_id"), rs.getInt("chunks")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	static String toVectorLiteral(float[] vector) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		return sb.append(']').toString();
	}

}
