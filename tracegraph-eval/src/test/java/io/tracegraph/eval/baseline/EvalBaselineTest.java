package io.tracegraph.eval.baseline;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.EvalResult;
import io.tracegraph.eval.MetricScore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvalBaselineTest {

    @Test
    void fromResultsBuildsMapKeyedByCaseId() {
        List<EvalResult<String>> results = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0))),
                result("case-2", List.of(MetricScore.fail("exact_match", 0.0, "no match")))
        );

        EvalBaseline baseline = EvalBaseline.from(results);

        assertThat(baseline.scoresByCaseId()).hasSize(2);
        assertThat(baseline.scoresByCaseId()).containsKeys("case-1", "case-2");
        assertThat(baseline.scoresFor("case-1")).isPresent();
        assertThat(baseline.scoresFor("case-1").get().get(0).score()).isEqualTo(1.0);
    }

    @Test
    void fromResultsCarriesAllMetricScores() {
        List<EvalResult<String>> results = List.of(
                result("case-1", List.of(
                        MetricScore.pass("exact_match", 1.0),
                        MetricScore.pass("contains", 0.8)
                ))
        );

        EvalBaseline baseline = EvalBaseline.from(results);

        List<MetricScore> scores = baseline.scoresFor("case-1").orElseThrow();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0).metricName()).isEqualTo("exact_match");
        assertThat(scores.get(1).metricName()).isEqualTo("contains");
    }

    @Test
    void scoresForReturnsEmptyWhenCaseUnknown() {
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        ));

        Optional<List<MetricScore>> missing = baseline.scoresFor("nonexistent");

        assertThat(missing).isEmpty();
    }

    @Test
    void recordIsImmutable() {
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        ));

        assertThat(baseline.scoresByCaseId().getClass().getName())
                .doesNotContain("HashMap");
    }

    @Test
    void createdAtIsPopulated() {
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        ));

        assertThat(baseline.createdAt()).isNotNull();
    }

    private static EvalResult<String> result(String id, List<MetricScore> scores) {
        EvalCase<String> evalCase = EvalCase.of(id, "input-" + id, "expected");
        boolean passed = scores.stream().allMatch(MetricScore::passed);
        return new EvalResult<>(evalCase, "actual-" + id, scores, 10L, passed);
    }
}
