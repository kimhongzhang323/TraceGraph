package io.tracegraph.connectors.cache;

import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.core.spi.EmbeddingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link LlmClient} decorator that caches responses by semantic similarity of the prompt.
 *
 * <p>On each call the last user message is embedded and compared against all cached prompts via
 * cosine similarity. If the best match exceeds {@link #similarityThreshold()} the cached response
 * is returned without hitting the underlying client. Cache misses are stored automatically.
 *
 * <p>The cache is in-memory and unbounded by default — use {@code maxSize} to cap it. Thread-safe.
 *
 * <pre>{@code
 * LlmClient cached = SemanticLlmCache.wrap(openAiClient)
 *     .embeddingClient(embedder)
 *     .similarityThreshold(0.95)
 *     .maxSize(500)
 *     .build();
 * }</pre>
 */
public final class SemanticLlmCache implements LlmClient {

    private record CacheEntry(float[] embedding, String promptKey, LlmResponse response) {}

    private final LlmClient delegate;
    private final EmbeddingClient embeddingClient;
    private final double similarityThreshold;
    private final int maxSize;
    private final CopyOnWriteArrayList<CacheEntry> entries = new CopyOnWriteArrayList<>();

    private SemanticLlmCache(Builder b) {
        this.delegate = b.delegate;
        this.embeddingClient = b.embeddingClient;
        this.similarityThreshold = b.similarityThreshold;
        this.maxSize = b.maxSize;
    }

    public static Builder wrap(LlmClient delegate) {
        return new Builder(delegate);
    }

    public double similarityThreshold() {
        return similarityThreshold;
    }

    /** Number of entries currently in the cache. */
    public int size() {
        return entries.size();
    }

    /** Clear all cached entries. */
    public void clear() {
        entries.clear();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String promptText = lastUserMessage(request);
        if (promptText == null || promptText.isBlank()) {
            return delegate.complete(request);
        }

        List<float[]> embeddings = embeddingClient.embed(List.of(promptText));
        if (embeddings.isEmpty() || embeddings.get(0).length == 0) {
            return delegate.complete(request);
        }
        float[] queryEmb = embeddings.get(0);

        // Search for a cached match
        List<CacheEntry> snapshot = new ArrayList<>(entries);
        double bestScore = -1;
        LlmResponse bestResponse = null;
        for (CacheEntry entry : snapshot) {
            double sim = cosineSimilarity(queryEmb, entry.embedding());
            if (sim > bestScore) {
                bestScore = sim;
                bestResponse = entry.response();
            }
        }

        if (bestScore >= similarityThreshold) {
            return bestResponse;
        }

        // Cache miss — call delegate and store
        LlmResponse response = delegate.complete(request);
        synchronized (this) {
            if (entries.size() >= maxSize) {
                entries.remove(0); // evict oldest
            }
            entries.add(new CacheEntry(queryEmb, promptText, response));
        }
        return response;
    }

    private static String lastUserMessage(LlmRequest request) {
        List<io.tracegraph.connectors.llm.ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            var m = messages.get(i);
            if (m.role() == io.tracegraph.connectors.llm.ChatMessage.Role.USER) {
                return m.content();
            }
        }
        return null;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    public static final class Builder {
        private final LlmClient delegate;
        private EmbeddingClient embeddingClient;
        private double similarityThreshold = 0.95;
        private int maxSize = 1000;

        private Builder(LlmClient delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        public Builder embeddingClient(EmbeddingClient ec) {
            this.embeddingClient = Objects.requireNonNull(ec, "embeddingClient");
            return this;
        }

        public Builder similarityThreshold(double t) {
            if (t < 0 || t > 1) throw new IllegalArgumentException("threshold must be in [0,1]");
            this.similarityThreshold = t;
            return this;
        }

        public Builder maxSize(int n) {
            if (n < 1) throw new IllegalArgumentException("maxSize must be >= 1");
            this.maxSize = n;
            return this;
        }

        public SemanticLlmCache build() {
            Objects.requireNonNull(embeddingClient, "embeddingClient is required");
            return new SemanticLlmCache(this);
        }
    }
}
