package com.example.helpdesk.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.example.helpdesk.TestcontainersConfiguration;
import com.example.helpdesk.assist.EmbeddingClient;
import com.example.helpdesk.user.Role;
import com.example.helpdesk.user.User;

// Real Postgres with pgvector (Testcontainers), hand-made vectors: checks the SQL, the vector
// literal format and that <=> orders by cosine distance.
@DataJpaTest
@Import({ TestcontainersConfiguration.class, KbChunkStore.class })
class KbChunkStoreTests {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private KbChunkStore store;

	private KbArticle passwords;

	private KbArticle billing;

	@BeforeEach
	void setUp() {
		User author = entityManager.persist(new User("kb@example.com", "KB", Role.AGENT, "{noop}unused"));
		passwords = entityManager.persist(new KbArticle("Passwords", "...", author));
		billing = entityManager.persist(new KbArticle("Billing", "...", author));
		entityManager.flush();
	}

	// A unit vector pointing mostly along one axis
	private static float[] towards(int axis, float wobble) {
		float[] vector = new float[EmbeddingClient.DIMENSIONS];
		vector[axis] = 1;
		vector[axis + 1] = wobble;
		return vector;
	}

	@Test
	void nearestOrdersByCosineDistance() {
		store.replace(passwords.getId(), List.of(new KbChunkStore.EmbeddedChunk("reset", towards(0, 0.1f)),
				new KbChunkStore.EmbeddedChunk("sso", towards(0, 0.9f))));
		store.replace(billing.getId(), List.of(new KbChunkStore.EmbeddedChunk("invoices", towards(10, 0))));

		List<KbChunkStore.Match> matches = store.nearest(towards(0, 0), 3);

		assertThat(matches).extracting(KbChunkStore.Match::content).containsExactly("reset", "sso", "invoices");
		assertThat(matches.getFirst().distance()).isLessThan(0.01);
		// Orthogonal vectors: cosine distance 1 (unrelated)
		assertThat(matches.getLast().distance()).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	void replaceSwapsAllChunksOfOneArticle() {
		store.replace(passwords.getId(), List.of(new KbChunkStore.EmbeddedChunk("old 1", towards(0, 0)),
				new KbChunkStore.EmbeddedChunk("old 2", towards(1, 0))));
		store.replace(billing.getId(), List.of(new KbChunkStore.EmbeddedChunk("billing", towards(5, 0))));

		store.replace(passwords.getId(), List.of(new KbChunkStore.EmbeddedChunk("new", towards(2, 0))));

		assertThat(store.count(passwords.getId())).isEqualTo(1);
		assertThat(store.counts()).containsEntry(passwords.getId(), 1).containsEntry(billing.getId(), 1);
	}

	@Test
	void deletingTheArticleDeletesItsChunks() {
		store.replace(passwords.getId(), List.of(new KbChunkStore.EmbeddedChunk("reset", towards(0, 0))));

		entityManager.remove(passwords);
		entityManager.flush();

		assertThat(store.count(passwords.getId())).isZero();
	}

}
