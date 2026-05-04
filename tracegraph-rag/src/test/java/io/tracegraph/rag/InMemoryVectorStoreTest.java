package io.tracegraph.rag;

import io.tracegraph.core.spi.VectorStore.VectorMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InMemoryVectorStoreTest {

    @Test
    void upsertAndQueryReturnsSimilarDocuments() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("s1", "doc1", new float[]{1f, 0f, 0f}, Map.of("k", "v1"));
        store.upsert("s1", "doc2", new float[]{0f, 1f, 0f}, Map.of("k", "v2"));

        List<VectorMatch> results = store.query("s1", new float[]{1f, 0f, 0f}, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo("doc1");
        assertThat(results.get(0).score()).isCloseTo(1.0f, within(0.0001f));
        assertThat(results.get(1).id()).isEqualTo("doc2");
    }

    @Test
    void deletedEntryNotReturnedInQuery() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("s1", "doc1", new float[]{1f, 0f}, Map.of());
        store.upsert("s1", "doc2", new float[]{0f, 1f}, Map.of());
        store.delete("s1", "doc1");

        List<VectorMatch> results = store.query("s1", new float[]{1f, 0f}, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("doc2");
    }

    @Test
    void queryScopedIsolation() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("scopeA", "docA", new float[]{1f, 0f}, Map.of());
        store.upsert("scopeB", "docB", new float[]{0f, 1f}, Map.of());

        List<VectorMatch> resultsA = store.query("scopeA", new float[]{1f, 0f}, 10);
        List<VectorMatch> resultsB = store.query("scopeB", new float[]{1f, 0f}, 10);

        assertThat(resultsA).extracting(VectorMatch::id).containsExactly("docA");
        assertThat(resultsB).extracting(VectorMatch::id).containsExactly("docB");
    }

    @Test
    void cosineSimilarityOfIdenticalVectorsIsOne() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("s", "id1", new float[]{1f, 0f, 0f}, Map.of());

        List<VectorMatch> results = store.query("s", new float[]{1f, 0f, 0f}, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isCloseTo(1.0f, within(0.0001f));
    }
}
