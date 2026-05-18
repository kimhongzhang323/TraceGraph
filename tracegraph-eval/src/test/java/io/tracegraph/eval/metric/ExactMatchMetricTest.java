package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExactMatchMetricTest {

    @Test
    void passesWhenActualEqualsExpected() {
        var metric = new ExactMatchMetric<String>();

        MetricScore score = metric.score(EvalCase.of("c1", "input", "answer"), "answer", 0);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isEqualTo(1.0);
    }

    @Test
    void failsWhenActualDiffersFromExpected() {
        var metric = new ExactMatchMetric<String>();

        MetricScore score = metric.score(EvalCase.of("c1", "input", "answer"), "wrong", 0);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.0);
        assertThat(score.detail()).contains("expected=answer").contains("actual=wrong");
    }
}
