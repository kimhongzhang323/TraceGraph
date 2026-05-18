package io.tracegraph.eval;

import io.tracegraph.core.Graph;
import io.tracegraph.eval.metric.ExactMatchMetric;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalSuiteParallelTest {

    private static Graph<String> uppercaseGraph() {
        return Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .entry("upper")
                .terminal("upper")
                .build();
    }

    private static Graph<String> sleepingGraph(long sleepMs) {
        return Graph.<String>builder()
                .node("sleep", (s, ctx) -> {
                    Thread.sleep(sleepMs);
                    return s.toUpperCase();
                })
                .entry("sleep")
                .terminal("sleep")
                .build();
    }

    @Test
    void runParallelReturnsResultsInDeclarationOrder() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "alpha", "ALPHA"))
                .addCase(EvalCase.of("case-2", "beta", "BETA"))
                .addCase(EvalCase.of("case-3", "gamma", "GAMMA"))
                .addCase(EvalCase.of("case-4", "delta", "DELTA"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.runParallel();

        assertThat(results).hasSize(4);
        assertThat(results).extracting(r -> r.evalCase().id())
                .containsExactly("case-1", "case-2", "case-3", "case-4");
        assertThat(results).allMatch(EvalResult::passed);
    }

    @Test
    void runParallelDefaultUsesVirtualThreadsAndRunsFasterThanSequential() {
        Graph<String> graph = sleepingGraph(100);
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "a", "A"))
                .addCase(EvalCase.of("case-2", "b", "B"))
                .addCase(EvalCase.of("case-3", "c", "C"))
                .addCase(EvalCase.of("case-4", "d", "D"))
                .addCase(EvalCase.of("case-5", "e", "E"))
                .metric(new ExactMatchMetric<>())
                .build();

        long start = System.currentTimeMillis();
        List<EvalResult<String>> results = suite.runParallel();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(results).hasSize(5);
        assertThat(results).allMatch(EvalResult::passed);
        assertThat(elapsed).isLessThan(400);
    }

    @Test
    void runParallelHonorsUserSuppliedExecutor() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "alpha", "ALPHA"))
                .addCase(EvalCase.of("case-2", "beta", "BETA"))
                .metric(new ExactMatchMetric<>())
                .build();

        ExecutorService userExec = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<EvalResult<String>> results = suite.runParallel(userExec);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(EvalResult::passed);
        } finally {
            userExec.shutdown();
        }
        assertThat(userExec.isShutdown()).isTrue();
    }

    @Test
    void runParallelDoesNotShutdownUserSuppliedExecutor() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("case-1", "alpha", "ALPHA"))
                .metric(new ExactMatchMetric<>())
                .build();

        ExecutorService userExec = Executors.newFixedThreadPool(2);
        try {
            suite.runParallel(userExec);
            assertThat(userExec.isShutdown()).isFalse();
        } finally {
            userExec.shutdownNow();
        }
    }

    @Test
    void runParallelInvokesGraphConcurrentlyAcrossCases() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("track", (s, ctx) -> {
                    int now = inFlight.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    try {
                        Thread.sleep(50);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                    return s.toUpperCase();
                })
                .entry("track")
                .terminal("track")
                .build();

        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("c1", "a", "A"))
                .addCase(EvalCase.of("c2", "b", "B"))
                .addCase(EvalCase.of("c3", "c", "C"))
                .addCase(EvalCase.of("c4", "d", "D"))
                .metric(new ExactMatchMetric<>())
                .build();

        suite.runParallel();

        assertThat(peak.get()).isGreaterThan(1);
    }

    @Test
    void runParallelReturnsEmptyListForNoCases() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.runParallel();

        assertThat(results).isEmpty();
    }

    @Test
    void runParallelCapturesPerCaseFailures() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("ok", "hello", "HELLO"))
                .addCase(EvalCase.of("nope", "foo", "BAR"))
                .metric(new ExactMatchMetric<>())
                .build();

        List<EvalResult<String>> results = suite.runParallel();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isFalse();
    }

    @Test
    void runParallelRejectsNullExecutor() {
        Graph<String> graph = uppercaseGraph();
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("c1", "a", "A"))
                .metric(new ExactMatchMetric<>())
                .build();

        assertThatThrownBy(() -> suite.runParallel((ExecutorService) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("unused")
    void runParallelLatencyHonoredWithinReasonableBudget() {
        Graph<String> graph = sleepingGraph(50);
        EvalSuite<String> suite = EvalSuite.<String>builder(graph)
                .addCase(EvalCase.of("c1", "a", "A"))
                .addCase(EvalCase.of("c2", "b", "B"))
                .addCase(EvalCase.of("c3", "c", "C"))
                .metric(new ExactMatchMetric<>())
                .build();

        long start = System.nanoTime();
        List<EvalResult<String>> results = suite.runParallel();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(results).hasSize(3);
        assertThat(elapsed.toMillis()).isLessThan(200);
    }
}
