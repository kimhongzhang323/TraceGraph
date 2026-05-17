package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataLoggingTest {

    @Test
    void rawIoIsNullByDefault() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "seed");
        recorder.recordEnter("eid", "node", 1, "seed");
        recorder.recordExit("eid", "node", 1, "seed", "out", 0L);
        recorder.recordComplete("eid", Status.COMPLETED, "out");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        assertThat(trace.steps().get(0).rawInput()).isNull();
        assertThat(trace.steps().get(0).rawOutput()).isNull();
    }

    @Test
    void rawIoRecordedWhenReported() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "seed");
        recorder.recordEnter("eid", "node", 1, "seed");
        recorder.recordRawIO("eid", "node", "prompt text", "response text");
        recorder.recordExit("eid", "node", 1, "seed", "out", 0L);
        recorder.recordComplete("eid", Status.COMPLETED, "out");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        assertThat(trace.steps().get(0).rawInput()).isEqualTo("prompt text");
        assertThat(trace.steps().get(0).rawOutput()).isEqualTo("response text");
    }

    @Test
    void rawIoClearedBetweenConsecutiveSteps() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "s");
        recorder.recordEnter("eid", "n", 1, "s");
        recorder.recordRawIO("eid", "n", "in1", "out1");
        recorder.recordExit("eid", "n", 1, "s", "mid", 0L);
        recorder.recordEnter("eid", "n2", 1, "mid");
        recorder.recordExit("eid", "n2", 1, "mid", "final", 0L);
        recorder.recordComplete("eid", Status.COMPLETED, "final");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        assertThat(trace.steps().get(0).rawInput()).isEqualTo("in1");
        assertThat(trace.steps().get(0).rawOutput()).isEqualTo("out1");
        assertThat(trace.steps().get(1).rawInput()).isNull();
        assertThat(trace.steps().get(1).rawOutput()).isNull();
    }

    @Test
    void graphWithSensitiveDataLoggingDisabledDoesNotCaptureRawIo() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("node", (s, ctx) -> {
                    ctx.reportRawIO("prompt", "response");
                    return s + ".out";
                })
                .entry("node").terminal("node")
                .sensitiveDataLogging(false)
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        assertThat(trace.steps().get(0).rawInput()).isNull();
        assertThat(trace.steps().get(0).rawOutput()).isNull();
    }

    @Test
    void graphWithSensitiveDataLoggingEnabledCapturesRawIo() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("node", (s, ctx) -> {
                    ctx.reportRawIO("the prompt", "the response");
                    return s + ".out";
                })
                .entry("node").terminal("node")
                .sensitiveDataLogging(true)
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        assertThat(trace.steps().get(0).rawInput()).isEqualTo("the prompt");
        assertThat(trace.steps().get(0).rawOutput()).isEqualTo("the response");
    }

    @Test
    void sensitiveDataLoggingDefaultIsFalse() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("node", (s, ctx) -> {
                    assertThat(ctx.sensitiveDataLoggingEnabled()).isFalse();
                    ctx.reportRawIO("prompt", "response");
                    return s + ".out";
                })
                .entry("node").terminal("node")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        assertThat(trace.steps().get(0).rawInput()).isNull();
    }

    @Test
    void sensitiveDataLoggingEnabledReturnsTrue() {
        Graph<String> graph = Graph.<String>builder()
                .node("node", (s, ctx) -> {
                    assertThat(ctx.sensitiveDataLoggingEnabled()).isTrue();
                    return s;
                })
                .entry("node").terminal("node")
                .sensitiveDataLogging(true)
                .build();

        graph.run("seed");
    }
}
