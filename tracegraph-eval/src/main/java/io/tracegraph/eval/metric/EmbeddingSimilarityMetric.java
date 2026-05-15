package io.tracegraph.eval.metric;

import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

import java.util.List;
import java.util.Objects;

public final class EmbeddingSimilarityMetric<S> implements Metric<S> {

    private final EmbeddingClient embeddingClient;
    private final double passThreshold;

    private EmbeddingSimilarityMetric(Builder<S> b) {
        this.embeddingClient = b.embeddingClient;
        this.passThreshold = b.passThreshold;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public double passThreshold() {
        return passThreshold;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        String expectedText = String.valueOf(evalCase.expected());
        String actualText = String.valueOf(actual);

        List<float[]> embeddings = embeddingClient.embed(List.of(expectedText, actualText));
        if (embeddings.size() < 2 || embeddings.get(0).length == 0) {
            return MetricScore.fail("embedding-similarity", 0.0,
                    "EmbeddingClient returned insufficient embeddings");
        }

        double similarity = cosineSimilarity(embeddings.get(0), embeddings.get(1));

        if (similarity >= passThreshold) {
            return MetricScore.pass("embedding-similarity", similarity);
        }
        return MetricScore.fail("embedding-similarity", similarity,
                "Cosine similarity " + String.format("%.4f", similarity)
                + " below threshold " + passThreshold);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    public static final class Builder<S> {
        private EmbeddingClient embeddingClient;
        private double passThreshold = 0.8;

        private Builder() {}

        public Builder<S> embeddingClient(EmbeddingClient client) {
            this.embeddingClient = Objects.requireNonNull(client, "embeddingClient");
            return this;
        }

        public Builder<S> passThreshold(double threshold) {
            if (threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("passThreshold must be in [0, 1]");
            }
            this.passThreshold = threshold;
            return this;
        }

        public EmbeddingSimilarityMetric<S> build() {
            Objects.requireNonNull(embeddingClient, "embeddingClient is required");
            return new EmbeddingSimilarityMetric<>(this);
        }
    }
}
