package io.tracegraph.rag;

import io.tracegraph.core.Graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class RagPipeline {

    private final Graph<RagState> graph;

    private RagPipeline(Builder b) {
        this.graph = buildGraph(b);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Graph<RagState> graph() {
        return graph;
    }

    private static Graph<RagState> buildGraph(Builder b) {
        return Graph.<RagState>builder()
                .node("retrieve", (state, ctx) -> {
                    List<String> chunks = b.retriever.apply(state.query());
                    return new RagState(state.query(), chunks, state.rankedChunks(), state.context());
                })
                .node("rerank", (state, ctx) -> {
                    List<RankedChunk> ranked = b.reranker.rerank(state.query(), state.chunks());
                    return new RagState(state.query(), state.chunks(), ranked, state.context());
                })
                .node("stuff", (state, ctx) -> {
                    StringBuilder sb = new StringBuilder();
                    for (RankedChunk chunk : state.rankedChunks()) {
                        sb.append(chunk.text()).append('\n');
                    }
                    String context = sb.toString().trim();
                    return new RagState(state.query(), state.chunks(), state.rankedChunks(), context);
                })
                .edge("retrieve", "rerank")
                .edge("rerank", "stuff")
                .entry("retrieve")
                .terminal("stuff")
                .build();
    }

    public static final class Builder {
        private Function<String, List<String>> retriever;
        private Reranker reranker = (query, chunks) -> {
            List<RankedChunk> ranked = new ArrayList<>(chunks.size());
            for (String chunk : chunks) ranked.add(new RankedChunk(chunk, 1.0));
            return List.copyOf(ranked);
        };

        private Builder() {}

        public Builder retriever(Function<String, List<String>> retriever) {
            this.retriever = Objects.requireNonNull(retriever, "retriever");
            return this;
        }

        public Builder reranker(Reranker reranker) {
            this.reranker = Objects.requireNonNull(reranker, "reranker");
            return this;
        }

        public RagPipeline build() {
            Objects.requireNonNull(retriever, "retriever is required");
            return new RagPipeline(this);
        }
    }
}
