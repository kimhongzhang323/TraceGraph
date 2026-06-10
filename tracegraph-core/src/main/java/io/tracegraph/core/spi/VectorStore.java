package io.tracegraph.core.spi;

import java.util.List;
import java.util.Map;

/**
 * Scoped vector store SPI for nearest-neighbour recall. Implementations must be thread-safe.
 *
 * <p>Concrete implementations live in {@code tracegraph-rag} or connector modules; this SPI
 * keeps {@code tracegraph-core} dependency-free. The default {@link #noop()} discards all writes
 * and returns empty query results.
 */
public interface VectorStore {

    /**
     * Insert or replace a vector entry identified by {@code id} within {@code scope}.
     *
     * @param scope     logical namespace (e.g. session id, collection name)
     * @param id        unique identifier within the scope
     * @param embedding dense float vector
     * @param metadata  arbitrary string attributes stored alongside the vector
     */
    void upsert(String scope, String id, float[] embedding, Map<String, String> metadata);

    /**
     * Return the {@code topK} nearest entries to {@code embedding} within {@code scope}, ordered
     * by descending similarity score.
     *
     * @param scope     logical namespace
     * @param embedding query vector
     * @param topK      maximum number of results
     * @return ranked matches; never {@code null}
     */
    List<VectorMatch> query(String scope, float[] embedding, int topK);

    /**
     * Remove the entry identified by {@code id} from {@code scope}. No-op if absent.
     *
     * @param scope logical namespace
     * @param id    entry to remove
     */
    void delete(String scope, String id);

    /**
     * Returns a no-op {@link VectorStore} that silently discards writes and returns empty results.
     * Useful as a default when no vector store is wired.
     */
    static VectorStore noop() {
        return new VectorStore() {
            @Override
            public void upsert(String scope, String id, float[] embedding,
                               Map<String, String> metadata) {
            }

            @Override
            public List<VectorMatch> query(String scope, float[] embedding, int topK) {
                return List.of();
            }

            @Override
            public void delete(String scope, String id) {
            }
        };
    }

    /**
     * A single result returned by {@link VectorStore#query}. {@code score} is similarity in the
     * range defined by the backing implementation (higher is more similar).
     */
    record VectorMatch(String id, float score, Map<String, String> metadata) {}
}
