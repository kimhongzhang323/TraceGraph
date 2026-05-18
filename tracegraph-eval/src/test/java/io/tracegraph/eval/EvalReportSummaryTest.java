package io.tracegraph.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportSummaryTest {

    @Test
    void summaryMarkdownIncludesAggregateBlockAndCaseTable() {
        List<EvalResult<String>> results = List.of(
                new EvalResult<>(EvalCase.of("c1", "in", "out"), "actual",
                        List.of(MetricScore.pass("m", 1.0)), 10L, true),
                new EvalResult<>(EvalCase.of("c2", "in", "out"), "actual",
                        List.of(MetricScore.fail("m", 0.0, "bad")), 20L, false));

        String markdown = EvalReport.toSummaryMarkdown(results);

        assertThat(markdown).contains("## Summary");
        assertThat(markdown).contains("Pass rate");
        assertThat(markdown).contains("50.00%");
        assertThat(markdown).contains("1/2");
        assertThat(markdown).contains("Latency p50");
        assertThat(markdown).contains("p95");
        assertThat(markdown).contains("p99");
        assertThat(markdown).contains("Mean score by metric");
        assertThat(markdown).contains("m");
        assertThat(markdown).contains("c1");
        assertThat(markdown).contains("c2");
        assertThat(markdown).contains("PASS");
        assertThat(markdown).contains("FAIL");
    }

    @Test
    void summaryMarkdownHandlesEmptyResults() {
        String markdown = EvalReport.toSummaryMarkdown(List.of());

        assertThat(markdown).contains("## Summary");
        assertThat(markdown).contains("0/0");
        assertThat(markdown).contains("Case ID");
    }
}
