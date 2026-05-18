package io.tracegraph.eval;

import io.tracegraph.core.Graph;
import io.tracegraph.eval.metric.ExactMatchMetric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalSuiteAssertionTest {

    private static Graph<String> uppercaseGraph() {
        return Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .entry("upper")
                .terminal("upper")
                .build();
    }

    @Test
    void assertPassedDoesNotThrowWhenAllCasesPass() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "world", "WORLD"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        suite.assertPassed(results);
    }

    @Test
    void assertPassedThrowsWhenAnyCaseFails() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThatThrownBy(() -> suite.assertPassed(results))
                .isInstanceOf(EvalAssertionException.class)
                .hasMessageContaining("case-2");
    }

    @Test
    void minPassRateAllowsPartialFailures() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "world", "WORLD"))
                .addCase(EvalCase.of("case-3", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .minPassRate(0.5)
                .build();

        List<EvalResult<String>> results = suite.run();

        suite.assertPassed(results);
    }

    @Test
    void minPassRateFailsWhenBelowThreshold() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "WRONG"))
                .addCase(EvalCase.of("case-2", "world", "ALSO_WRONG"))
                .addCase(EvalCase.of("case-3", "foo", "FOO"))
                .metric(new ExactMatchMetric<>())
                .minPassRate(0.75)
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThatThrownBy(() -> suite.assertPassed(results))
                .isInstanceOf(EvalAssertionException.class)
                .hasMessageContaining("pass rate");
    }

    @Test
    void minPassRateOfZeroAlwaysPasses() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "WRONG"))
                .metric(new ExactMatchMetric<>())
                .minPassRate(0.0)
                .build();

        List<EvalResult<String>> results = suite.run();

        suite.assertPassed(results);
    }

    @Test
    void minPassRateRejectsOutOfRangeValues() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite.Builder<String> builder = EvalSuite.<String>builder(graph);

        assertThatThrownBy(() -> builder.minPassRate(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.minPassRate(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failFastStopsAtFirstFailure() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "world", "WRONG"))
                .addCase(EvalCase.of("case-3", "foo", "FOO"))
                .metric(new ExactMatchMetric<>())
                .failFast(true)
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isFalse();
    }

    @Test
    void failFastFalseRunsAllCases() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "world", "WRONG"))
                .addCase(EvalCase.of("case-3", "foo", "FOO"))
                .metric(new ExactMatchMetric<>())
                .failFast(false)
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results).hasSize(3);
    }

    @Test
    void defaultBehaviorRunsAllCases() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "world", "WRONG"))
                .addCase(EvalCase.of("case-3", "foo", "FOO"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        assertThat(results).hasSize(3);
    }

    @Test
    void evalAssertionExceptionExposesResults() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "hello", "HELLO"))
                .addCase(EvalCase.of("case-2", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.run();

        try {
            suite.assertPassed(results);
        } catch (EvalAssertionException e) {
            assertThat(e.results()).hasSize(2);
            assertThat(e.failedResults()).hasSize(1);
            assertThat(e.failedResults().get(0).evalCase().id()).isEqualTo("case-2");
            assertThat(e.passRate()).isEqualTo(0.5);
        }
    }
}
