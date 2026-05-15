package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingSimilarityMetricTest {

    /** Returns identical unit vectors so cosine similarity == 1.0. */
    private static float[] unit() {
        return new float[]{1.0f, 0.0f, 0.0f};
    }

    /** Returns orthogonal vector so cosine similarity == 0.0. */
    private static float[] orthogonal() {
        return new float[]{0.0f, 1.0f, 0.0f};
    }

    @Test
    void passesWhenVectorsIdentical() {
        var metric = EmbeddingSimilarityMetric.<String>builder()
                .embeddingClient(texts -> List.of(unit(), unit()))
                .passThreshold(0.9)
                .build();

        MetricScore score = metric.score(EvalCase.of("c1", "q", "expected"), "actual", 10);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isEqualTo(1.0);
    }

    @Test
    void failsWhenVectorsOrthogonal() {
        var metric = EmbeddingSimilarityMetric.<String>builder()
                .embeddingClient(texts -> List.of(unit(), orthogonal()))
                .passThreshold(0.8)
                .build();

        MetricScore score = metric.score(EvalCase.of("c1", "q", "expected"), "actual", 10);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.0);
    }

    @Test
    void failsWhenEmbeddingClientReturnsEmpty() {
        var metric = EmbeddingSimilarityMetric.<String>builder()
                .embeddingClient(texts -> List.of(new float[0], new float[0]))
                .passThreshold(0.8)
                .build();

        MetricScore score = metric.score(EvalCase.of("c1", "q", "e"), "a", 10);

        assertThat(score.passed()).isFalse();
        assertThat(score.detail()).contains("insufficient embeddings");
    }

    @Test
    void requiresEmbeddingClient() {
        assertThatThrownBy(() -> EmbeddingSimilarityMetric.<String>builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> EmbeddingSimilarityMetric.<String>builder()
                .embeddingClient(t -> List.of())
                .passThreshold(-0.1)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
