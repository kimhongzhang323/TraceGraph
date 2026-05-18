package io.tracegraph.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvalSummaryTest {

    @Test
    void summaryComputesPassRateAndCounts() {
        List<EvalResult<String>> results = List.of(
                result("c1", true, 10, MetricScore.pass("m", 1.0)),
                result("c2", true, 20, MetricScore.pass("m", 1.0)),
                result("c3", false, 30, MetricScore.fail("m", 0.0, "bad")));

        EvalSummary summary = EvalSummary.from(results);

        assertThat(summary.totalCases()).isEqualTo(3);
        assertThat(summary.passedCases()).isEqualTo(2);
        assertThat(summary.failedCases()).isEqualTo(1);
        assertThat(summary.passRate()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void summaryMeanScorePerMetricIgnoresNanScores() {
        List<EvalResult<String>> results = List.of(
                result("c1", true, 10, MetricScore.pass("m", 1.0), MetricScore.pass("n", 0.5)),
                result("c2", true, 20, MetricScore.pass("m", 0.5), MetricScore.pass("n", Double.NaN)));

        EvalSummary summary = EvalSummary.from(results);

        assertThat(summary.meanScoreByMetric()).containsEntry("m", 0.75);
        assertThat(summary.meanScoreByMetric()).containsEntry("n", 0.5);
    }

    @Test
    void latencyPercentilesAreNearestRankOnSorted() {
        List<EvalResult<String>> results = List.of(
                result("c1", true, 10),
                result("c2", true, 20),
                result("c3", true, 30),
                result("c4", true, 40),
                result("c5", true, 50),
                result("c6", true, 60),
                result("c7", true, 70),
                result("c8", true, 80),
                result("c9", true, 90),
                result("c10", true, 100));

        EvalSummary summary = EvalSummary.from(results);

        assertThat(summary.latencyP50Ms()).isEqualTo(50L);
        assertThat(summary.latencyP95Ms()).isEqualTo(100L);
        assertThat(summary.latencyP99Ms()).isEqualTo(100L);
    }

    @Test
    void emptyResultsProduceZeroSummary() {
        EvalSummary summary = EvalSummary.from(List.of());

        assertThat(summary.totalCases()).isZero();
        assertThat(summary.passedCases()).isZero();
        assertThat(summary.failedCases()).isZero();
        assertThat(summary.passRate()).isEqualTo(0.0);
        assertThat(summary.latencyP50Ms()).isZero();
        assertThat(summary.latencyP95Ms()).isZero();
        assertThat(summary.latencyP99Ms()).isZero();
        assertThat(summary.meanScoreByMetric()).isEmpty();
    }

    @Test
    void singleResultReturnsThatLatencyForAllPercentiles() {
        List<EvalResult<String>> results = List.of(result("c1", true, 42));

        EvalSummary summary = EvalSummary.from(results);

        assertThat(summary.latencyP50Ms()).isEqualTo(42L);
        assertThat(summary.latencyP95Ms()).isEqualTo(42L);
        assertThat(summary.latencyP99Ms()).isEqualTo(42L);
    }

    @Test
    void meanScoreByMetricPreservesInsertionOrder() {
        List<EvalResult<String>> results = List.of(
                result("c1", true, 10,
                        MetricScore.pass("third", 1.0),
                        MetricScore.pass("first", 1.0),
                        MetricScore.pass("second", 1.0)));

        EvalSummary summary = EvalSummary.from(results);

        assertThat(summary.meanScoreByMetric().keySet())
                .containsExactly("third", "first", "second");
    }

    private static EvalResult<String> result(String id, boolean passed, long latencyMs,
                                             MetricScore... scores) {
        return new EvalResult<>(EvalCase.of(id, "in", "out"), "actual", List.of(scores), latencyMs, passed);
    }
}
