package io.tracegraph.eval;

import io.tracegraph.core.Graph;
import io.tracegraph.eval.metric.ContainsMetric;
import io.tracegraph.eval.metric.ExactMatchMetric;
import io.tracegraph.eval.metric.LatencyMetric;
import io.tracegraph.eval.metric.Metric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalSuiteTest {

    private static Graph<String> uppercaseGraph() {
        return Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .entry("upper")
                .terminal("upper")
                .build();
    }

    @Test
    void exactMatchPassesWhenOutputMatches() {
        Graph<String> graph = uppercaseGraph();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "world", "WORLD"))
                .addCase(EvalCase.of("case-3", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results).hasSize(3);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(0).actualOutput()).isEqualTo("HELLO");
        assertThat(results.get(1).passed()).isTrue();
        assertThat(results.get(2).passed()).isFalse();
        assertThat(results.get(2).scores().get(0).passed()).isFalse();
    }

    @Test
    void containsMetricMatchesSubstring() {
        Graph<String> graph = uppercaseGraph();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello world", "WORLD"))
                .addCase(EvalCase.of("case-2", "hello world", "MISSING"))
                .metric(new ContainsMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isFalse();
    }

    @Test
    void latencyMetricPassesForFastGraph() {
        Graph<String> graph = uppercaseGraph();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "test", "TEST"))
                .metric(new LatencyMetric<>(5000))
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(0).scores().get(0).score()).isGreaterThan(0.0);
    }

    @Test
    void multipleMetricsAllMustPassForOverallPass() {
        Graph<String> graph = uppercaseGraph();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .metric(new ExactMatchMetric<>())
                .metric(new ContainsMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results.get(0).scores()).hasSize(2);
        assertThat(results.get(0).passed()).isTrue();
    }

    @Test
    void toMarkdownRendersSkippedScoreAsEmDash() {
        EvalResult<String> result = new EvalResult<>(
                EvalCase.of("case-1", "input", null),
                "actual",
                List.of(MetricScore.skipped("conditional")),
                10L,
                true);

        String markdown = EvalReport.toMarkdown(List.of(result));

        assertThat(markdown).contains("conditional");
        assertThat(markdown).contains("—");
        assertThat(markdown).doesNotContain("NaN");
    }

    @Test
    void toMarkdownContainsExpectedContent() {
        Graph<String> graph = uppercaseGraph();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();
        String markdown = EvalReport.toMarkdown(results);

        assertThat(markdown).contains("Case ID");
        assertThat(markdown).contains("Passed");
        assertThat(markdown).contains("case-1");
        assertThat(markdown).contains("PASS");
        assertThat(markdown).contains("case-2");
        assertThat(markdown).contains("FAIL");
        assertThat(markdown).contains("exact_match");
    }

    @Test
    void skippedMetricYieldsNanScoreAndDoesNotFailCase() {
        Graph<String> graph = uppercaseGraph();

        Metric<String> conditional = new Metric<>() {
            @Override
            public boolean canScore(EvalCase<String> evalCase) {
                return evalCase.expected() != null;
            }

            @Override
            public MetricScore score(EvalCase<String> evalCase, String actual, long latencyMs) {
                return MetricScore.pass("conditional", 1.0);
            }
        };

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("with-expected", "hello", "HELLO"))
                .addCase(EvalCase.of("no-expected", "hello", null))
                .metric(conditional)
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results).hasSize(2);
        MetricScore first = results.get(0).scores().get(0);
        assertThat(first.passed()).isTrue();
        assertThat(first.score()).isEqualTo(1.0);

        MetricScore skipped = results.get(1).scores().get(0);
        assertThat(skipped.passed()).isTrue();
        assertThat(Double.isNaN(skipped.score())).isTrue();
        assertThat(skipped.detail()).isEqualTo("skipped");
        assertThat(results.get(1).passed()).isTrue();
    }

    @Test
    void toJUnitXmlIsWellFormed() {
        Graph<String> graph = uppercaseGraph();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();
        String xml = EvalReport.toJUnitXml(results, "MyEvalSuite");

        assertThat(xml).startsWith("<?xml");
        assertThat(xml).contains("<testsuite");
        assertThat(xml).contains("name=\"MyEvalSuite\"");
        assertThat(xml).contains("tests=\"2\"");
        assertThat(xml).contains("failures=\"1\"");
        assertThat(xml).contains("case-1");
        assertThat(xml).contains("case-2");
        assertThat(xml).contains("<failure");
        assertThat(xml).contains("</testsuite>");
    }
}
