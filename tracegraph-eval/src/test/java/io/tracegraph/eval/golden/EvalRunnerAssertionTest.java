package io.tracegraph.eval.golden;

import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.RecordingTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalRunnerAssertionTest {

    private static ExecutionTrace<String> trace(String id, String initial, String finalSt) {
        return new ExecutionTrace<>(id, initial, finalSt, Status.COMPLETED, null,
                List.of(), Instant.now(), Instant.now());
    }

    private static Graph<String> uppercaseGraph(InMemoryTraceStore runStore) {
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(runStore);
        return Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .entry("upper")
                .terminal("upper")
                .traceRecorder(recorder)
                .build();
    }

    @Test
    void passingAssertionLeavesResultPassedAndFailuresEmpty() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .runStore(runStore)
                .assertion("upper", step -> Optional.empty())
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isTrue();
        assertThat(r.assertionFailures()).isEmpty();
    }

    @Test
    void failingAssertionFlipsResultToFailedAndPopulatesFailures() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .runStore(runStore)
                .assertion("upper", step -> Optional.of("after was " + step.after()))
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.assertionFailures()).hasSize(1);
        AssertionFailure f = r.assertionFailures().get(0);
        assertThat(f.nodeName()).isEqualTo("upper");
        assertThat(f.stepIndex()).isZero();
        assertThat(f.message()).isEqualTo("after was HELLO");
    }

    @Test
    void assertionsOnlyRunAgainstMatchingNodeName() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .runStore(runStore)
                .assertion("nonexistent", step -> Optional.of("should never fire"))
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isTrue();
        assertThat(r.assertionFailures()).isEmpty();
    }

    @Test
    void multipleAssertionsOnSameNodeAccumulateFailures() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .runStore(runStore)
                .assertion("upper", step -> Optional.of("first"))
                .assertion("upper", step -> Optional.of("second"))
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.assertionFailures()).extracting(AssertionFailure::message)
                .containsExactly("first", "second");
    }

    @Test
    void assertionRunsAgainstEveryMatchingStep() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(runStore);
        // Two-node graph so the trace has two steps, one matching "upper", one not.
        Graph<String> graph = Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .node("suffix", (s, ctx) -> s + "!")
                .entry("upper")
                .edge("upper", "suffix")
                .terminal("suffix")
                .traceRecorder(recorder)
                .build();

        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .runStore(runStore)
                .assertion("suffix", step -> step.after().endsWith("!")
                        ? Optional.empty()
                        : Optional.of("missing !"))
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO!"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isTrue();
        assertThat(r.assertionFailures()).isEmpty();
    }

    @Test
    void assertionFailureAloneFailsCaseEvenWhenFinalStateMatches() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        // Final state matches the golden but the assertion fails — passed should be false.
        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .runStore(runStore)
                .assertion("upper", step -> Optional.of("auditor says no"))
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.diff()).isNotNull();
        assertThat(r.diff().sameFinalState()).isTrue();
        assertThat(r.passed()).isFalse();
        assertThat(r.assertionFailures()).hasSize(1);
    }

    @Test
    void builderRejectsNullGraph() {
        assertThatThrownBy(() -> EvalRunner.<String>builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderRejectsNullAssertion() {
        assertThatThrownBy(() -> EvalRunner.<String>builder()
                .assertion("upper", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderRejectsNullNodeName() {
        assertThatThrownBy(() -> EvalRunner.<String>builder()
                .assertion(null, step -> Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderWithoutRunStoreSkipsAssertionsButStillRuns() {
        // No runStore wired: state-only comparison, assertions silently no-op.
        Graph<String> graph = Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .entry("upper")
                .terminal("upper")
                .build();

        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(graph)
                .assertion("upper", step -> Optional.of("should never fire"))
                .build();

        List<GoldenEvalResult<String>> results = runner.run(
                List.of(new GoldenTrace<>(trace("g-1", "hello", "HELLO"))));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isTrue();
        assertThat(r.assertionFailures()).isEmpty();
        assertThat(r.actual()).isNull();
    }

    @Test
    void traceAssertionSamSignatureIsStable() {
        TraceAssertion<String> alwaysOk = step -> Optional.empty();
        assertThat(alwaysOk.check(null)).isEmpty();
    }
}
