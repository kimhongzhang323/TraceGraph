package io.tracegraph.rag;

import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.core.spi.VectorStore.VectorMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral contract every {@link VectorStore} implementation must satisfy.
 * Implementations extend this class and supply a fresh store per test.
 */
public abstract class VectorStoreContractTest {

    protected abstract VectorStore createStore();

    @Test
    void queryOnEmptyScopeReturnsNoMatches() {
        VectorStore store = createStore();
        assertThat(store.query("scope", new float[]{1f, 0f}, 5)).isEmpty();
    }

    @Test
    void upsertThenQueryReturnsEntryWithMetadata() {
        VectorStore store = createStore();
        store.upsert("scope", "a", new float[]{1f, 0f}, Map.of("k", "v"));

        List<VectorMatch> matches = store.query("scope", new float[]{1f, 0f}, 5);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().id()).isEqualTo("a");
        assertThat(matches.getFirst().metadata()).containsEntry("k", "v");
    }

    @Test
    void resultsAreOrderedByDescendingSimilarity() {
        VectorStore store = createStore();
        store.upsert("scope", "near", new float[]{1f, 0f}, Map.of());
        store.upsert("scope", "far", new float[]{0f, 1f}, Map.of());

        List<VectorMatch> matches = store.query("scope", new float[]{1f, 0f}, 5);

        assertThat(matches).extracting(VectorMatch::id).containsExactly("near", "far");
        assertThat(matches.get(0).score()).isGreaterThanOrEqualTo(matches.get(1).score());
    }

    @Test
    void topKLimitsResultCount() {
        VectorStore store = createStore();
        store.upsert("scope", "a", new float[]{1f, 0f}, Map.of());
        store.upsert("scope", "b", new float[]{0.9f, 0.1f}, Map.of());
        store.upsert("scope", "c", new float[]{0.8f, 0.2f}, Map.of());

        assertThat(store.query("scope", new float[]{1f, 0f}, 2)).hasSize(2);
    }

    @Test
    void upsertWithSameIdReplacesEntry() {
        VectorStore store = createStore();
        store.upsert("scope", "a", new float[]{1f, 0f}, Map.of("v", "1"));
        store.upsert("scope", "a", new float[]{1f, 0f}, Map.of("v", "2"));

        List<VectorMatch> matches = store.query("scope", new float[]{1f, 0f}, 5);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().metadata()).containsEntry("v", "2");
    }

    @Test
    void deleteRemovesEntryAndMissingIdIsNoOp() {
        VectorStore store = createStore();
        store.upsert("scope", "a", new float[]{1f, 0f}, Map.of());

        store.delete("scope", "a");
        store.delete("scope", "ghost");

        assertThat(store.query("scope", new float[]{1f, 0f}, 5)).isEmpty();
    }

    @Test
    void scopesAreIsolated() {
        VectorStore store = createStore();
        store.upsert("scope-1", "a", new float[]{1f, 0f}, Map.of());

        assertThat(store.query("scope-2", new float[]{1f, 0f}, 5)).isEmpty();
    }
}
