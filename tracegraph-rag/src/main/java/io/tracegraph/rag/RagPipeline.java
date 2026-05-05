package io.tracegraph.rag;

import io.tracegraph.core.Graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Small ready-made retrieval pipeline expressed as a TraceGraph graph.
 *
 * <p>The pipeline runs three steps in order: retrieve raw chunks, rerank them, then stuff the
 * ranked chunk text into a single context string. It is intentionally simple and designed as a
 * composable starting point rather than a full end-user agent.
 */
public final class RagPipeline {

    private final Graph<RagState> graph;

    private RagPipeline(Builder b) {
        this.graph = buildGraph(b);
    }

    /**
     * Create a builder for a RAG pipeline.
     *
     * @return pipeline builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the compiled graph backing this pipeline.
     *
     * @return graph that operates on {@link RagState}
     */
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

    /**
     * Builder for {@link RagPipeline}.
     */
    public static final class Builder {
        private Function<String, List<String>> retriever;
        private Reranker reranker = (query, chunks) -> {
            List<RankedChunk> ranked = new ArrayList<>(chunks.size());
            for (String chunk : chunks) ranked.add(new RankedChunk(chunk, 1.0));
            return List.copyOf(ranked);
        };

        private Builder() {}

        /**
         * Provide the retrieval function that maps a query to raw text chunks.
         *
         * @param retriever retrieval function
         * @return this builder
         */
        public Builder retriever(Function<String, List<String>> retriever) {
            this.retriever = Objects.requireNonNull(retriever, "retriever");
            return this;
        }

        /**
         * Provide the reranker used after retrieval.
         *
         * @param reranker reranker implementation
         * @return this builder
         */
        public Builder reranker(Reranker reranker) {
            this.reranker = Objects.requireNonNull(reranker, "reranker");
            return this;
        }

        /**
         * Validate the configuration and build the pipeline.
         *
         * @return pipeline
         */
        public RagPipeline build() {
            Objects.requireNonNull(retriever, "retriever is required");
            return new RagPipeline(this);
        }
    }
}
