package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.core.exec.GraphValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayRunnerTest {

    private static Graph<String> linearGraph(InMemoryTraceStore store) {
        return Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ExecutionTrace<String> traceFor(InMemoryTraceStore store, String id) {
        return (ExecutionTrace<String>) store.load(id).orElseThrow();
    }

    @Test
    void reRunFromEntryReExecutesEntireGraph() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> g = linearGraph(store);
        ExecutionResult<String> r = g.run("seed");
        ExecutionTrace<String> parent = traceFor(store, r.executionId());

        ExecutionResult<String> forkResult = ReplayRunner.of(parent, g).reRunFrom(-1);

        assertThat(forkResult.status()).isEqualTo(Status.COMPLETED);
        assertThat(forkResult.finalState()).isEqualTo("seed.a.b.c");
        ExecutionTrace<String> forkTrace = traceFor(store, forkResult.executionId());
        assertThat(forkTrace.steps()).hasSize(3);
        assertThat(forkTrace.forkedFromExecutionId()).isEqualTo(parent.executionId());
        assertThat(forkTrace.forkedFromStepIndex()).isEqualTo(-1);
        assertThat(forkTrace.initialState()).isEqualTo("seed");
    }

    @Test
    void reRunFromMiddleUsesStepBeforeStateAsSeed() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> g = linearGraph(store);
        ExecutionResult<String> r = g.run("x");
        ExecutionTrace<String> parent = traceFor(store, r.executionId());

        ExecutionResult<String> forkResult = ReplayRunner.of(parent, g).reRunFrom(1);

        assertThat(forkResult.finalState()).isEqualTo("x.a.b.c");
        ExecutionTrace<String> forkTrace = traceFor(store, forkResult.executionId());
        assertThat(forkTrace.steps()).hasSize(2);
        assertThat(forkTrace.steps().get(0).nodeName()).isEqualTo("b");
        assertThat(forkTrace.steps().get(1).nodeName()).isEqualTo("c");
        assertThat(forkTrace.initialState()).isEqualTo("x.a");
        assertThat(forkTrace.forkedFromStepIndex()).isEqualTo(1);
    }

    @Test
    void seedOverrideReplacesDefaultBeforeState() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> g = linearGraph(store);
        ExecutionResult<String> r = g.run("orig");
        ExecutionTrace<String> parent = traceFor(store, r.executionId());

        ExecutionResult<String> forkResult = ReplayRunner.of(parent, g).reRunFrom(1, "FORK");

        assertThat(forkResult.finalState()).isEqualTo("FORK.b.c");
        ExecutionTrace<String> forkTrace = traceFor(store, forkResult.executionId());
        assertThat(forkTrace.initialState()).isEqualTo("FORK");
    }

    @Test
    void whatIfAgainstModifiedGraphProducesDifferentTrace() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> original = linearGraph(store);
        ExecutionResult<String> r = original.run("seed");
        ExecutionTrace<String> parent = traceFor(store, r.executionId());

        Graph<String> modified = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".B-MODIFIED")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a").edge("a", "b").edge("b", "c").terminal("c")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> forkResult = ReplayRunner.of(parent, modified).reRunFrom(1);
        assertThat(forkResult.finalState()).isEqualTo("seed.a.B-MODIFIED.c");
    }

    @Test
    void rejectsStepIndexOutOfRange() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> g = linearGraph(store);
        ExecutionResult<String> r = g.run("s");
        ExecutionTrace<String> parent = traceFor(store, r.executionId());
        ReplayRunner<String> runner = ReplayRunner.of(parent, g);

        assertThatThrownBy(() -> runner.reRunFrom(99)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> runner.reRunFrom(-2)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void rejectsForkOnNodeMissingFromCurrentGraph() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> original = linearGraph(store);
        ExecutionResult<String> r = original.run("s");
        ExecutionTrace<String> parent = traceFor(store, r.executionId());

        Graph<String> withoutB = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a").edge("a", "c").terminal("c")
                .build();

        assertThatThrownBy(() -> ReplayRunner.of(parent, withoutB).reRunFrom(1))
                .isInstanceOf(GraphValidationException.class);
    }

    @Test
    void normalRunHasNullForkLineage() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> g = linearGraph(store);
        ExecutionResult<String> r = g.run("s");
        ExecutionTrace<String> trace = traceFor(store, r.executionId());

        assertThat(trace.forkedFromExecutionId()).isNull();
        assertThat(trace.forkedFromStepIndex()).isEqualTo(-1);
        assertThat(trace.isFork()).isFalse();
    }
}
