package io.tracegraph.rag;

import java.util.List;

/**
 * State object carried through the built-in retrieval-augmented generation pipeline.
 *
 * @param query original user query
 * @param chunks raw retrieved chunks
 * @param rankedChunks reranked chunks with scores
 * @param context stuffed context string ready to feed into a model prompt
 */
public record RagState(
        String query,
        List<String> chunks,
        List<RankedChunk> rankedChunks,
        String context
) {
    /**
     * Create an empty state for a query before retrieval has started.
     *
     * @param query user query
     * @return initial pipeline state
     */
    public static RagState of(String query) {
        return new RagState(query, List.of(), List.of(), "");
    }
}
