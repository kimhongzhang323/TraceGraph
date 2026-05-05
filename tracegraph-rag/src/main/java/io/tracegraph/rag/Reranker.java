package io.tracegraph.rag;

import java.util.List;

@FunctionalInterface
public interface Reranker {
    List<RankedChunk> rerank(String query, List<String> chunks);
}
