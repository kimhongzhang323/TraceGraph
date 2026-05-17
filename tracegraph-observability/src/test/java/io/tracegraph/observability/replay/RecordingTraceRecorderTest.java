package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingTraceRecorderTest {

    @Test
    void persistsCompletedTraceWithSteps() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        assertThat(trace.status()).isEqualTo(Status.COMPLETED);
        assertThat(trace.initialState()).isEqualTo("seed");
        assertThat(trace.finalState()).isEqualTo("seed.a.b");
        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.steps().get(0).nodeName()).isEqualTo("a");
        assertThat(trace.steps().get(0).before()).isEqualTo("seed");
        assertThat(trace.steps().get(0).after()).isEqualTo("seed.a");
        assertThat(trace.steps().get(1).nodeName()).isEqualTo("b");
        assertThat(trace.steps().get(1).after()).isEqualTo("seed.a.b");
    }

    @Test
    void recordsFailedTraceWithErrorStep() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("ok", (s, ctx) -> s + ".ok")
                .node("boom", (s, ctx) -> { throw new RuntimeException("x"); })
                .entry("ok")
                .edge("ok", "boom")
                .terminal("boom")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        assertThat(trace.status()).isEqualTo(Status.FAILED);
        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.steps().get(0).failed()).isFalse();
        TraceStep<String> failed = trace.steps().get(1);
        assertThat(failed.failed()).isTrue();
        assertThat(failed.nodeName()).isEqualTo("boom");
        assertThat(failed.error()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void subgraphChildTraceCarriesParentLineage() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        Graph<String> inner = Graph.<String>builder()
                .node("i1", (s, ctx) -> s + ".i1")
                .node("i2", (s, ctx) -> s + ".i2")
                .entry("i1").edge("i1", "i2").terminal("i2")
                .traceRecorder(recorder)
                .build();

        Graph<String> outer = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .subgraph("sub", inner)
                .node("b", (s, ctx) -> s + ".b")
                .entry("a").edge("a", "sub").edge("sub", "b").terminal("b")
                .traceRecorder(recorder)
                .build();

        ExecutionResult<String> result = outer.run("seed");
        String parentEid = result.executionId();

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> parentTrace = (ExecutionTrace<String>) store.load(parentEid).orElseThrow();
        @SuppressWarnings("unchecked")
        ExecutionTrace<String> childTrace = (ExecutionTrace<String>) store.load(parentEid + ":sub").orElseThrow();

        assertThat(parentTrace.isChild()).isFalse();
        assertThat(parentTrace.parentExecutionId()).isNull();
        assertThat(parentTrace.parentStepIndex()).isEqualTo(-1);

        assertThat(childTrace.isChild()).isTrue();
        assertThat(childTrace.parentExecutionId()).isEqualTo(parentEid);
        assertThat(childTrace.parentStepIndex()).isEqualTo(1);
        assertThat(parentTrace.steps().get(1).nodeName()).isEqualTo("sub");
    }

    @Test
    void nonSubgraphTraceHasNoParentLineage() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .entry("a").terminal("a")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        assertThat(trace.isChild()).isFalse();
        assertThat(trace.parentExecutionId()).isNull();
        assertThat(trace.parentStepIndex()).isEqualTo(-1);
    }

    @Test
    void concurrentRunsKeepDistinctTraces() throws Exception {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n")
                .entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        Thread t1 = new Thread(() -> graph.run("first"));
        Thread t2 = new Thread(() -> graph.run("second"));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(store.size()).isEqualTo(2);
    }
}
