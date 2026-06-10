package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedactingTraceStoreTest {

    private static TraceStep<String> step(String rawInput, String rawOutput, Throwable error) {
        return new TraceStep<>(0, "node", 1, "before", "after",
                Duration.ofMillis(1), error, List.of(), null, rawInput, rawOutput);
    }

    private static ExecutionTrace<String> trace(TraceStep<String> step) {
        return new ExecutionTrace<>("exec-1", "init", "final", Status.COMPLETED, null,
                List.of(step), Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
    }

    // Assembled at runtime so secret scanners don't flag the fixtures.
    private static String fake(String... parts) {
        return String.join("", parts);
    }

    @Test
    void redactsRawIoBeforeDelegating() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        RedactingTraceStore store = new RedactingTraceStore(delegate, Redactor.defaultPatterns());

        store.save(trace(step("call with " + fake("sk-", "abcdefghijklmnop1234"),
                fake("Bearer ", "abcdefghijklmnop1234"), null)));

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> saved = (ExecutionTrace<String>) delegate.load("exec-1").orElseThrow();
        assertThat(saved.steps().getFirst().rawInput()).isEqualTo("call with [REDACTED]");
        assertThat(saved.steps().getFirst().rawOutput()).isEqualTo("[REDACTED]");
    }

    @Test
    void redactsErrorMessagesAndKeepsClassName() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        RedactingTraceStore store = new RedactingTraceStore(delegate, Redactor.defaultPatterns());

        store.save(trace(step(null, null,
                new IllegalStateException("auth failed for " + fake("api", "_key=", "supersecret123")))));

        ExecutionTrace<?> saved = delegate.load("exec-1").orElseThrow();
        Throwable error = saved.steps().getFirst().error();
        assertThat(error.getMessage())
                .contains("java.lang.IllegalStateException")
                .contains("[REDACTED]")
                .doesNotContain("supersecret123");
    }

    @Test
    void leavesCleanContentUntouched() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        RedactingTraceStore store = new RedactingTraceStore(delegate, Redactor.defaultPatterns());
        RuntimeException original = new RuntimeException("plain failure");

        store.save(trace(step("hello", "world", original)));

        ExecutionTrace<?> saved = delegate.load("exec-1").orElseThrow();
        assertThat(saved.steps().getFirst().rawInput()).isEqualTo("hello");
        assertThat(saved.steps().getFirst().rawOutput()).isEqualTo("world");
        assertThat(saved.steps().getFirst().error()).isSameAs(original);
    }

    @Test
    void delegatesLoadDeleteAndListIds() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        RedactingTraceStore store = new RedactingTraceStore(delegate, Redactor.NONE);
        store.save(trace(step(null, null, null)));

        assertThat(store.listIds()).containsExactly("exec-1");
        assertThat(store.load("exec-1")).isPresent();
        store.delete("exec-1");
        assertThat(store.load("exec-1")).isEmpty();
    }

    @Test
    void nullRawIoSurvivesRedaction() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        RedactingTraceStore store = new RedactingTraceStore(delegate, Redactor.defaultPatterns());

        store.save(trace(step(null, null, null)));

        ExecutionTrace<?> saved = delegate.load("exec-1").orElseThrow();
        assertThat(saved.steps().getFirst().rawInput()).isNull();
        assertThat(saved.steps().getFirst().rawOutput()).isNull();
    }
}
