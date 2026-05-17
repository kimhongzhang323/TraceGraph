package io.tracegraph.eval.golden;

import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.RecordingTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalRunnerTest {

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
    void passesWhenGraphReproducesGoldenFinalState() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        GoldenTrace<String> golden = new GoldenTrace<>(trace("g-1", "hello", "HELLO"));
        EvalRunner<String> runner = EvalRunner.of(graph, runStore);

        List<GoldenEvalResult<String>> results = runner.run(List.of(golden));

        assertThat(results).hasSize(1);
        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isTrue();
        assertThat(r.golden()).isSameAs(golden);
        assertThat(r.actual()).isNotNull();
        assertThat(r.actual().finalState()).isEqualTo("HELLO");
        assertThat(r.diff()).isNotNull();
        assertThat(r.diff().sameStatus()).isTrue();
        assertThat(r.diff().sameFinalState()).isTrue();
        assertThat(r.error()).isNull();
    }

    @Test
    void failsWhenGraphProducesDifferentFinalState() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        // Golden says "foo" → "WRONG" but graph will produce "FOO"
        GoldenTrace<String> golden = new GoldenTrace<>(trace("g-2", "foo", "WRONG"));
        EvalRunner<String> runner = EvalRunner.of(graph, runStore);

        List<GoldenEvalResult<String>> results = runner.run(List.of(golden));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.diff().sameFinalState()).isFalse();
    }

    @Test
    void handlesMultipleGoldenTraces() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);

        List<GoldenTrace<String>> goldens = List.of(
                new GoldenTrace<>(trace("g-1", "hello", "HELLO")),
                new GoldenTrace<>(trace("g-2", "world", "WORLD")),
                new GoldenTrace<>(trace("g-3", "foo", "WRONG"))
        );
        EvalRunner<String> runner = EvalRunner.of(graph, runStore);

        List<GoldenEvalResult<String>> results = runner.run(goldens);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isTrue();
        assertThat(results.get(2).passed()).isFalse();
    }

    @Test
    void capturesErrorWhenGraphThrows() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(runStore);
        Graph<String> graph = Graph.<String>builder()
                .node("boom", (s, ctx) -> { throw new RuntimeException("kaboom"); })
                .entry("boom")
                .terminal("boom")
                .traceRecorder(recorder)
                .build();

        GoldenTrace<String> golden = new GoldenTrace<>(trace("g-err", "input", "EXPECTED"));
        EvalRunner<String> runner = EvalRunner.of(graph, runStore);

        List<GoldenEvalResult<String>> results = runner.run(List.of(golden));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.error()).isNotNull();
        assertThat(r.diff()).isNull();
    }

    @Test
    void runWithoutRunStoreUsesStateDiffOnly() {
        // Graph without a recorder — EvalRunner falls back to state-only comparison
        Graph<String> graph = Graph.<String>builder()
                .node("upper", (s, ctx) -> s.toUpperCase())
                .entry("upper")
                .terminal("upper")
                .build();

        GoldenTrace<String> golden = new GoldenTrace<>(trace("g-1", "hello", "HELLO"));
        EvalRunner<String> runner = EvalRunner.of(graph);

        List<GoldenEvalResult<String>> results = runner.run(List.of(golden));

        GoldenEvalResult<String> r = results.get(0);
        assertThat(r.passed()).isTrue();
        assertThat(r.actual()).isNull();
        assertThat(r.diff()).isNull();
    }

    @Test
    void rejectsNullGraph() {
        assertThatThrownBy(() -> EvalRunner.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullGoldenList() {
        InMemoryTraceStore runStore = new InMemoryTraceStore();
        Graph<String> graph = uppercaseGraph(runStore);
        EvalRunner<String> runner = EvalRunner.of(graph, runStore);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(NullPointerException.class);
    }
}
