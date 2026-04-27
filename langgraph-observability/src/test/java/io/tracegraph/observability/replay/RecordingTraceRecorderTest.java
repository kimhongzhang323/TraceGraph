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
