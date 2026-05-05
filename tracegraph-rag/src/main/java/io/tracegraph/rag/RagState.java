package io.tracegraph.rag;

import java.util.List;

public record RagState(
        String query,
        List<String> chunks,
        List<RankedChunk> rankedChunks,
        String context
) {
    public static RagState of(String query) {
        return new RagState(query, List.of(), List.of(), "");
    }
}
