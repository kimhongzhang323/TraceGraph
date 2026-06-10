package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

abstract class TraceStoreContractTest {

    protected abstract TraceStore createStore();

    private TraceStore store;

    @BeforeEach
    void createStoreUnderTest() {
        store = createStore();
    }

    private static ExecutionTrace<String> trace(String executionId, Status status, int stepCount) {
        Instant start = Instant.parse("2026-04-28T10:00:00Z");
        List<TraceStep<String>> steps = IntStream.range(0, stepCount)
                .<TraceStep<String>>mapToObj(i -> TraceStep.leaf(i, "node-" + i, 1,
                        "s" + i, "s" + (i + 1), Duration.ofMillis(5), null))
                .toList();
        return new ExecutionTrace<>(executionId, "s0", "s" + stepCount, status, null,
                steps, start, start.plusSeconds(1));
    }

    @Test
    void loadReturnsEmptyForUnknownId() {
        assertThat(store.load("unknown")).isEmpty();
    }

    @Test
    void saveThenLoadRoundTrips() {
        store.save(trace("exec-1", Status.COMPLETED, 2));

        Optional<ExecutionTrace<?>> loaded = store.load("exec-1");
        assertThat(loaded).isPresent();
        ExecutionTrace<?> t = loaded.get();
        assertThat(t.executionId()).isEqualTo("exec-1");
        assertThat(t.status()).isEqualTo(Status.COMPLETED);
        assertThat(t.steps()).hasSize(2);
    }

    @Test
    void secondSaveForSameExecutionWins() {
        store.save(trace("exec-1", Status.COMPLETED, 1));
        store.save(trace("exec-1", Status.COMPLETED, 3));

        assertThat(store.load("exec-1").orElseThrow().steps()).hasSize(3);
    }

    @Test
    void deleteRemovesTrace() {
        store.save(trace("exec-1", Status.COMPLETED, 1));
        store.delete("exec-1");
        assertThat(store.load("exec-1")).isEmpty();
    }

    @Test
    void listIdsContainsSavedIds() {
        store.save(trace("exec-1", Status.COMPLETED, 1));
        store.save(trace("exec-2", Status.COMPLETED, 1));

        assertThat(store.listIds()).containsExactlyInAnyOrder("exec-1", "exec-2");
    }
}
