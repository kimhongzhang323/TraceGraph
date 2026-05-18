package io.tracegraph.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricScoreTest {

    @Test
    void skippedFactoryYieldsPassingNanScore() {
        MetricScore score = MetricScore.skipped("llm-judge");

        assertThat(score.metricName()).isEqualTo("llm-judge");
        assertThat(score.passed()).isTrue();
        assertThat(Double.isNaN(score.score())).isTrue();
        assertThat(score.detail()).isEqualTo("skipped");
    }
}
