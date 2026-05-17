package io.tracegraph.connectors.vector;

import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.core.spi.VectorStore.VectorMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVectorStoreTest {

    @Test
    void queryReturnsNearestByCosine() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("col", "a", new float[]{1.0f, 0.0f}, Map.of("label", "a"));
        store.upsert("col", "b", new float[]{0.0f, 1.0f}, Map.of("label", "b"));
        store.upsert("col", "c", new float[]{1.0f, 1.0f}, Map.of("label", "c"));

        List<VectorMatch> results = store.query("col", new float[]{1.0f, 0.0f}, 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).id()).isEqualTo("a");
        assertThat(results.get(0).score()).isEqualTo(1.0f);
    }

    @Test
    void topKLimitsResults() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("col", "a", new float[]{1.0f, 0.0f}, Map.of());
        store.upsert("col", "b", new float[]{0.0f, 1.0f}, Map.of());
        store.upsert("col", "c", new float[]{1.0f, 1.0f}, Map.of());

        List<VectorMatch> results = store.query("col", new float[]{1.0f, 0.0f}, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("a");
    }

    @Test
    void deleteRemovesEntry() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("col", "x", new float[]{1.0f}, Map.of());
        store.delete("col", "x");

        List<VectorMatch> results = store.query("col", new float[]{1.0f}, 10);

        assertThat(results).isEmpty();
    }

    @Test
    void deleteOnAbsentIsNoOp() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.delete("nonexistent", "id");
        assertThat(store.query("nonexistent", new float[]{1.0f}, 5)).isEmpty();
    }

    @Test
    void queryScopedToNamespace() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("ns1", "a", new float[]{1.0f, 0.0f}, Map.of());
        store.upsert("ns2", "b", new float[]{1.0f, 0.0f}, Map.of());

        List<VectorMatch> ns1Results = store.query("ns1", new float[]{1.0f, 0.0f}, 10);
        List<VectorMatch> ns2Results = store.query("ns2", new float[]{1.0f, 0.0f}, 10);

        assertThat(ns1Results).extracting(VectorMatch::id).containsExactly("a");
        assertThat(ns2Results).extracting(VectorMatch::id).containsExactly("b");
    }

    @Test
    void upsertReplacesExistingEntry() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("col", "x", new float[]{1.0f, 0.0f}, Map.of("v", "1"));
        store.upsert("col", "x", new float[]{0.0f, 1.0f}, Map.of("v", "2"));

        List<VectorMatch> results = store.query("col", new float[]{0.0f, 1.0f}, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("x");
        assertThat(results.get(0).score()).isEqualTo(1.0f);
        assertThat(results.get(0).metadata()).containsEntry("v", "2");
    }

    @Test
    void metadataPreservedOnResult() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("col", "doc", new float[]{1.0f}, Map.of("source", "wiki", "page", "42"));

        List<VectorMatch> results = store.query("col", new float[]{1.0f}, 1);

        assertThat(results.get(0).metadata())
                .containsEntry("source", "wiki")
                .containsEntry("page", "42");
    }

    @Test
    void zeroNormQueryVectorScoresZero() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("col", "a", new float[]{1.0f, 0.0f}, Map.of());

        List<VectorMatch> results = store.query("col", new float[]{0.0f, 0.0f}, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.0f);
    }

    @Test
    void queryOnEmptyScopeReturnsEmpty() {
        InMemoryVectorStore store = new InMemoryVectorStore();

        assertThat(store.query("empty", new float[]{1.0f}, 5)).isEmpty();
    }

    @Test
    void implementsVectorStoreSpi() {
        VectorStore store = new InMemoryVectorStore();
        store.upsert("s", "id", new float[]{1.0f}, Map.of());
        assertThat(store.query("s", new float[]{1.0f}, 1)).hasSize(1);
    }
}
