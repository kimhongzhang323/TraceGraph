package io.tracegraph.rag;

import java.util.List;

/**
 * Scores retrieved chunks relative to a query and returns them in preferred order.
 */
@FunctionalInterface
public interface Reranker {
    /**
     * Rank candidate chunks for the supplied query.
     *
     * @param query user query
     * @param chunks retrieved candidate chunks
     * @return ranked chunks, typically highest relevance first
     */
    List<RankedChunk> rerank(String query, List<String> chunks);
}
