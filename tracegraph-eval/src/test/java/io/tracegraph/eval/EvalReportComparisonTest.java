package io.tracegraph.eval;

import io.tracegraph.eval.baseline.EvalBaseline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportComparisonTest {

    @Test
    void unchangedScoreShowsEqualMarker() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown).contains("case-1");
        assertThat(markdown).contains("exact_match");
        assertThat(markdown).contains("=");
    }

    @Test
    void regressionShowsDownArrow() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(MetricScore.fail("exact_match", 0.0, "regressed")))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown).contains("↓");
        assertThat(markdown).contains("-1.00");
    }

    @Test
    void improvementShowsUpArrow() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(MetricScore.fail("exact_match", 0.2, "low")))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 0.9)))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown).contains("↑");
        assertThat(markdown).contains("+0.70");
    }

    @Test
    void newCaseMarkedAsNew() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0))),
                result("case-2", List.of(MetricScore.pass("exact_match", 0.8)))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown).contains("case-2");
        assertThat(markdown).contains("NEW");
    }

    @Test
    void emptyCurrentReturnsHeaderOnly() {
        EvalBaseline baseline = EvalBaseline.from(List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        ));

        String markdown = EvalReport.toComparisonMarkdown(List.<EvalResult<String>>of(), baseline);

        assertThat(markdown).contains("Case ID");
        assertThat(markdown).contains("Metric");
        assertThat(markdown).contains("Baseline");
        assertThat(markdown).contains("Current");
    }

    @Test
    void markdownTableShapeMatchesExpectedHeader() {
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        );
        EvalBaseline baseline = EvalBaseline.from(currentResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown.lines().findFirst().orElseThrow())
                .contains("Case ID")
                .contains("Metric")
                .contains("Baseline")
                .contains("Current")
                .contains("Δ");
    }

    @Test
    void perCaseMultipleMetricsRenderAsSeparateRows() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(
                        MetricScore.pass("exact_match", 1.0),
                        MetricScore.pass("contains", 1.0)
                ))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(
                        MetricScore.pass("exact_match", 1.0),
                        MetricScore.fail("contains", 0.0, "missing")
                ))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        long case1Rows = markdown.lines().filter(line -> line.startsWith("| case-1")).count();
        assertThat(case1Rows).isEqualTo(2);
    }

    @Test
    void newMetricOnExistingCaseMarkedNew() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(MetricScore.pass("exact_match", 1.0)))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(
                        MetricScore.pass("exact_match", 1.0),
                        MetricScore.pass("contains", 0.5)
                ))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown).contains("contains");
        assertThat(markdown).contains("NEW");
    }

    @Test
    void skippedScoreRendersAsEmDashAndNoNaNText() {
        List<EvalResult<String>> baselineResults = List.of(
                result("case-1", List.of(MetricScore.skipped("llm-judge")))
        );
        List<EvalResult<String>> currentResults = List.of(
                result("case-1", List.of(MetricScore.skipped("llm-judge")))
        );
        EvalBaseline baseline = EvalBaseline.from(baselineResults);

        String markdown = EvalReport.toComparisonMarkdown(currentResults, baseline);

        assertThat(markdown).doesNotContain("NaN");
        assertThat(markdown).contains("—");
    }

    private static EvalResult<String> result(String id, List<MetricScore> scores) {
        EvalCase<String> evalCase = EvalCase.of(id, "input-" + id, "expected");
        boolean passed = scores.stream().allMatch(MetricScore::passed);
        return new EvalResult<>(evalCase, "actual-" + id, scores, 10L, passed);
    }
}
