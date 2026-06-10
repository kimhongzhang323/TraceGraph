package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingTraceRecorderFlushTest {

    @Test
    void flushesPartialTraceEveryNSteps() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store, 2);
        recorder.recordStart("exec-1", "init");

        recorder.recordExit("exec-1", "a", 1, "s0", "s1", 1L);
        assertThat(store.load("exec-1")).isEmpty();

        recorder.recordExit("exec-1", "b", 1, "s1", "s2", 1L);
        ExecutionTrace<?> partial = store.load("exec-1").orElseThrow();
        assertThat(partial.status()).isEqualTo(Status.RUNNING);
        assertThat(partial.steps()).hasSize(2);
        assertThat(partial.finalState()).isNull();
    }

    @Test
    void completionOverwritesPartialTrace() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store, 1);
        recorder.recordStart("exec-2", "init");

        recorder.recordExit("exec-2", "a", 1, "s0", "s1", 1L);
        recorder.recordComplete("exec-2", Status.COMPLETED, "s1");

        ExecutionTrace<?> trace = store.load("exec-2").orElseThrow();
        assertThat(trace.status()).isEqualTo(Status.COMPLETED);
        assertThat(trace.finalState()).isEqualTo("s1");
        assertThat(trace.steps()).hasSize(1);
    }

    @Test
    void errorStepTriggersFlush() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store, 1);
        recorder.recordStart("exec-3", "init");

        recorder.recordError("exec-3", "a", new RuntimeException("boom"));

        ExecutionTrace<?> partial = store.load("exec-3").orElseThrow();
        assertThat(partial.status()).isEqualTo(Status.RUNNING);
        assertThat(partial.steps().getFirst().failed()).isTrue();
    }

    @Test
    void zeroIntervalKeepsSaveOnCompletionOnly() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store, 0);
        recorder.recordStart("exec-4", "init");

        recorder.recordExit("exec-4", "a", 1, "s0", "s1", 1L);
        assertThat(store.load("exec-4")).isEmpty();

        recorder.recordComplete("exec-4", Status.COMPLETED, "s1");
        assertThat(store.load("exec-4")).isPresent();
    }

    @Test
    void rejectsNegativeFlushInterval() {
        assertThatThrownBy(() -> new RecordingTraceRecorder(new InMemoryTraceStore(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
