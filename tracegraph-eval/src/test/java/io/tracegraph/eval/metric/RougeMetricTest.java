package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RougeMetricTest {

    @Test
    void scoresOneForIdenticalText() {
        var metric = new RougeMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the quick brown fox",
                0);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isEqualTo(1.0);
    }

    @Test
    void scoresZeroForDisjointText() {
        var metric = new RougeMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "alpha beta gamma"),
                "one two three",
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.0);
    }

    @Test
    void usesLcsForRougeL() {
        // LCS of "the quick brown fox" and "the brown quick fox" is "the brown fox" (or "the quick fox") = 3 tokens.
        // ref length = cand length = 4 → recall = precision = 3/4 → F1 = 0.75
        var metric = new RougeMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the brown quick fox",
                0);

        assertThat(score.score()).isCloseTo(0.75, within(1e-9));
        assertThat(score.passed()).isTrue();
    }

    @Test
    void failsWhenF1BelowThreshold() {
        var metric = new RougeMetric<String>(0.9);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the slow red turtle",
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.detail()).contains("below threshold");
    }

    @Test
    void treatsNullActualAsEmpty() {
        var metric = new RougeMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "anything"),
                null,
                0);

        assertThat(score.score()).isEqualTo(0.0);
        assertThat(score.passed()).isFalse();
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new RougeMetric<String>(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RougeMetric<String>(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
