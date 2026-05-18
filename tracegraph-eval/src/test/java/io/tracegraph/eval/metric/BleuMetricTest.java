package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BleuMetricTest {

    @Test
    void scoresOneForIdenticalText() {
        var metric = new BleuMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox jumps over the lazy dog"),
                "the quick brown fox jumps over the lazy dog",
                0);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isEqualTo(1.0);
    }

    @Test
    void scoresZeroForCompletelyDisjointText() {
        var metric = new BleuMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "alpha beta gamma delta"),
                "one two three four",
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.0);
    }

    @Test
    void passesWhenScoreMeetsThreshold() {
        var metric = new BleuMetric<String>(0.1);

        // Significant 4-gram overlap; partial mismatch keeps score below 1.0 but above 0.1
        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox jumps over the lazy dog"),
                "the quick brown fox jumps over the lazy cat",
                0);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isGreaterThan(0.1).isLessThan(1.0);
    }

    @Test
    void failsWhenScoreBelowThreshold() {
        var metric = new BleuMetric<String>(0.9);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "the quick brown fox"),
                "the slow red turtle",
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isLessThan(0.9);
        assertThat(score.detail()).contains("below threshold");
    }

    @Test
    void treatsNullActualAsEmptyString() {
        var metric = new BleuMetric<String>(0.5);

        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "anything"),
                null,
                0);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.0);
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new BleuMetric<String>(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BleuMetric<String>(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultMaxNgramIsFour() {
        var metric = new BleuMetric<String>(0.5);

        assertThat(metric.maxNgram()).isEqualTo(4);
    }

    @Test
    void supportsConfigurableMaxNgram() {
        var metric = new BleuMetric<String>(0.5, 2);

        // Unigram + bigram overlap on first two tokens; trailing token differs.
        MetricScore score = metric.score(
                EvalCase.of("c1", "input", "hello world goodbye"),
                "hello world farewell",
                0);

        assertThat(metric.maxNgram()).isEqualTo(2);
        assertThat(score.score()).isGreaterThan(0.0);
    }
}
