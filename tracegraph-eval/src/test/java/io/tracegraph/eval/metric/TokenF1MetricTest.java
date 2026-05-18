package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TokenF1MetricTest {

    @Test
    void scoresOneForIdenticalText() {
        var metric = new TokenF1Metric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the quick brown fox",
                0);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isEqualTo(1.0);
    }

    @Test
    void scoresZeroForDisjointTokens() {
        var metric = new TokenF1Metric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "alpha beta gamma"),
                "one two three",
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.0);
    }

    @Test
    void computesHarmonicMeanForPartialOverlap() {
        // expected = "the quick brown fox" (4 tokens)
        // actual = "the brown fox dog cat" (5 tokens)
        // shared multiset = {the, brown, fox} (3) → precision = 3/5, recall = 3/4
        // F1 = 2 * (0.6 * 0.75) / (0.6 + 0.75) = 0.9 / 1.35 = 0.6666...
        var metric = new TokenF1Metric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the brown fox dog cat",
                0);

        assertThat(score.score()).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(score.passed()).isTrue();
    }

    @Test
    void handlesRepeatedTokensAsMultiset() {
        // expected = "the the the" (3 "the")
        // actual = "the the cat" (2 "the", 1 "cat")
        // shared = min(3, 2) "the" = 2
        // precision = 2/3, recall = 2/3 → F1 = 2/3
        var metric = new TokenF1Metric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the the the"),
                "the the cat",
                0);

        assertThat(score.score()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void failsWhenBelowThreshold() {
        var metric = new TokenF1Metric<String>(0.9);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the slow red turtle",
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.detail()).contains("below threshold");
    }

    @Test
    void treatsNullActualAsEmpty() {
        var metric = new TokenF1Metric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "anything"),
                null,
                0);

        assertThat(score.score()).isEqualTo(0.0);
        assertThat(score.passed()).isFalse();
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new TokenF1Metric<String>(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenF1Metric<String>(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
