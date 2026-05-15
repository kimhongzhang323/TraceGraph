package io.tracegraph.rag.hybrid;

import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combines dense vector retrieval and BM25 keyword retrieval via Reciprocal Rank Fusion (RRF).
 *
 * <p>RRF merges ranked lists without requiring score normalisation. Each document receives a
 * combined score of {@code 1/(k + rank_vector) + 1/(k + rank_bm25)}, where {@code k=60} is the
 * standard constant that dampens the impact of high-ranked results.
 *
 * <p>Requires a pre-built {@link Bm25Scorer} corpus that mirrors the vector store contents.
 *
 * <pre>{@code
 * Bm25Scorer bm25 = Bm25Scorer.builder().addDocuments(docs).build();
 * HybridRetriever retriever = HybridRetriever.builder()
 *     .vectorStore(vs).embeddingClient(ec).bm25Scorer(bm25)
 *     .scope("my-collection").build();
 * List<Document> results = retriever.retrieve("what is RAG?", 5);
 * }</pre>
 */
public final class HybridRetriever {

    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    private final Bm25Scorer bm25;
    private final String scope;
    private final int candidateMultiplier;

    private HybridRetriever(Builder b) {
        this.vectorStore = b.vectorStore;
        this.embeddingClient = b.embeddingClient;
        this.bm25 = b.bm25;
        this.scope = b.scope;
        this.candidateMultiplier = b.candidateMultiplier;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieve and fuse the top-{@code k} documents from dense + sparse retrieval.
     *
     * @param query query text
     * @param k     number of results to return
     * @return fused ranked list; {@link Document#score()} carries the RRF score
     */
    public List<Document> retrieve(String query, int k) {
        int candidates = k * candidateMultiplier;

        // Dense retrieval
        List<float[]> embeddings = embeddingClient.embed(List.of(query));
        List<VectorStore.VectorMatch> vectorMatches =
                vectorStore.query(scope, embeddings.get(0), candidates);

        // Sparse retrieval
        List<Document> bm25Results = bm25.score(query, candidates);

        // RRF fusion
        Map<String, double[]> rrfScores = new LinkedHashMap<>();
        Map<String, Document> docById = new HashMap<>();

        for (int rank = 0; rank < vectorMatches.size(); rank++) {
            VectorStore.VectorMatch m = vectorMatches.get(rank);
            rrfScores.computeIfAbsent(m.id(), id -> new double[1])[0] += 1.0 / (RRF_K + rank + 1);
            String text = m.metadata().getOrDefault("__text__", "");
            docById.putIfAbsent(m.id(), new Document(m.id(), text, m.metadata(), 0f));
        }
        for (int rank = 0; rank < bm25Results.size(); rank++) {
            Document d = bm25Results.get(rank);
            rrfScores.computeIfAbsent(d.id(), id -> new double[1])[0] += 1.0 / (RRF_K + rank + 1);
            docById.putIfAbsent(d.id(), d);
        }

        List<Map.Entry<String, double[]>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort((a, b2) -> Double.compare(b2.getValue()[0], a.getValue()[0]));

        List<Document> result = new ArrayList<>(Math.min(k, sorted.size()));
        for (int i = 0; i < Math.min(k, sorted.size()); i++) {
            String id = sorted.get(i).getKey();
            double rrfScore = sorted.get(i).getValue()[0];
            Document d = docById.get(id);
            result.add(new Document(d.id(), d.text(), d.metadata(), (float) rrfScore));
        }
        return List.copyOf(result);
    }

    public static final class Builder {
        private VectorStore vectorStore;
        private EmbeddingClient embeddingClient;
        private Bm25Scorer bm25;
        private String scope = "default";
        private int candidateMultiplier = 3;

        private Builder() {}

        public Builder vectorStore(VectorStore vs) {
            this.vectorStore = Objects.requireNonNull(vs, "vectorStore");
            return this;
        }

        public Builder embeddingClient(EmbeddingClient ec) {
            this.embeddingClient = Objects.requireNonNull(ec, "embeddingClient");
            return this;
        }

        public Builder bm25Scorer(Bm25Scorer bm25) {
            this.bm25 = Objects.requireNonNull(bm25, "bm25Scorer");
            return this;
        }

        public Builder scope(String scope) {
            this.scope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        /** How many candidates to fetch from each retriever before fusing. Default: k*3. */
        public Builder candidateMultiplier(int m) {
            if (m < 1) throw new IllegalArgumentException("candidateMultiplier must be >= 1");
            this.candidateMultiplier = m;
            return this;
        }

        public HybridRetriever build() {
            Objects.requireNonNull(vectorStore, "vectorStore is required");
            Objects.requireNonNull(embeddingClient, "embeddingClient is required");
            Objects.requireNonNull(bm25, "bm25Scorer is required");
            return new HybridRetriever(this);
        }
    }
}
