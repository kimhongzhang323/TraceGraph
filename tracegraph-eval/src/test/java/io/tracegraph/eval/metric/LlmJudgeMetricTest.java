package io.tracegraph.eval.metric;

import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.MockLlmClient;
import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmJudgeMetricTest {

    private static MockLlmClient respondingWith(String content) {
        return new MockLlmClient(req -> new LlmResponse(
                content, LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO));
    }

    @Test
    void passesWhenScoreAboveThreshold() {
        var metric = LlmJudgeMetric.<String>builder()
                .judge(respondingWith("0.9\nGreat answer."))
                .model("gpt-4o")
                .passThreshold(0.7)
                .build();

        MetricScore score = metric.score(EvalCase.of("c1", "question", "expected"), "actual", 100);

        assertThat(score.passed()).isTrue();
        assertThat(score.score()).isEqualTo(0.9);
    }

    @Test
    void failsWhenScoreBelowThreshold() {
        var metric = LlmJudgeMetric.<String>builder()
                .judge(respondingWith("0.3\nPoor answer."))
                .model("gpt-4o")
                .passThreshold(0.7)
                .build();

        MetricScore score = metric.score(EvalCase.of("c1", "q", "e"), "a", 50);

        assertThat(score.passed()).isFalse();
        assertThat(score.score()).isEqualTo(0.3);
    }

    @Test
    void failsWithDetailOnNonNumericResponse() {
        var metric = LlmJudgeMetric.<String>builder()
                .judge(respondingWith("excellent"))
                .model("gpt-4o")
                .build();

        MetricScore score = metric.score(EvalCase.of("c1", "q", "e"), "a", 50);

        assertThat(score.passed()).isFalse();
        assertThat(score.detail()).contains("non-numeric");
    }

    @Test
    void rejectsInvalidPassThreshold() {
        assertThatThrownBy(() -> LlmJudgeMetric.<String>builder()
                .judge(MockLlmClient.echoing())
                .model("m")
                .passThreshold(1.5)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresJudge() {
        assertThatThrownBy(() -> LlmJudgeMetric.<String>builder().model("m").build())
                .isInstanceOf(NullPointerException.class);
    }
}
