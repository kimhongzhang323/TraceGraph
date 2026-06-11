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
        Graph.Builder<RagState> gb = Graph.<RagState>builder()
                .node("retrieve", (state, ctx) -> {
                    List<String> chunks = b.retriever.apply(state.query());
                    ctx.reportRawIO(state.query(), "chunks=" + chunks.size());
                    return new RagState(state.query(), chunks, state.rankedChunks(), state.context());
                })
                .node("rerank", (state, ctx) -> {
                    List<RankedChunk> ranked = b.reranker.rerank(state.query(), state.chunks());
                    StringBuilder scores = new StringBuilder("ranked=").append(ranked.size());
                    for (RankedChunk chunk : ranked) {
                        scores.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", chunk.score()));
                    }
                    ctx.reportRawIO(state.query(), scores.toString());
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
                .terminal("stuff");
        if (b.traceRecorder != null) {
            gb.traceRecorder(b.traceRecorder);
        }
        gb.sensitiveDataLogging(b.sensitiveDataLogging);
        return gb.build();
    }

    /**
     * Builder for {@link RagPipeline}.
     */
    public static final class Builder {
        private Function<String, List<String>> retriever;
        private io.tracegraph.core.spi.TraceRecorder traceRecorder;
        private boolean sensitiveDataLogging;
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
         * Record the pipeline's executions (including the per-step retrieval reporting) through
         * {@code recorder} — typically a {@code RecordingTraceRecorder} or {@code LiveTraceFeed}.
         *
         * @param recorder trace recorder to wire into the compiled graph
         * @return this builder
         */
        public Builder traceRecorder(io.tracegraph.core.spi.TraceRecorder recorder) {
            this.traceRecorder = Objects.requireNonNull(recorder, "recorder");
            return this;
        }

        /**
         * Enable capture of the retrieval query and hit summary into traces. Queries are
         * prompt-shaped data, so they respect the same opt-in as raw LLM I/O (default off).
         *
         * @param enabled whether to record query/hit text into traces
         * @return this builder
         */
        public Builder sensitiveDataLogging(boolean enabled) {
            this.sensitiveDataLogging = enabled;
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
