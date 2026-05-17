package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SamplingTraceStoreTest {

    private static ExecutionTrace<String> trace(String id, Status status, Duration totalDuration) {
        Instant start = Instant.EPOCH;
        Instant end = start.plus(totalDuration);
        TraceStep<String> step = TraceStep.leaf(0, "n", 1, "in", "out", totalDuration, null);
        return new ExecutionTrace<>(id, "in", "out", status, null, List.of(step), start, end);
    }

    @Test
    void delegateReceivesSavesWhenStrategyAllows() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        SamplingTraceStore store = new SamplingTraceStore(delegate, t -> true);

        store.save(trace("e1", Status.COMPLETED, Duration.ofMillis(10)));

        assertThat(delegate.listIds()).containsExactly("e1");
    }

    @Test
    void strategyRejectsSavesAreDropped() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        SamplingTraceStore store = new SamplingTraceStore(delegate, t -> false);

        store.save(trace("e1", Status.COMPLETED, Duration.ofMillis(10)));

        assertThat(delegate.listIds()).isEmpty();
    }

    @Test
    void loadDeleteAndListIdsForwardToDelegate() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        delegate.save(trace("e1", Status.COMPLETED, Duration.ofMillis(10)));
        SamplingTraceStore store = new SamplingTraceStore(delegate, t -> true);

        assertThat(store.load("e1")).isPresent();
        assertThat(store.listIds()).containsExactly("e1");

        store.delete("e1");
        assertThat(delegate.load("e1")).isEmpty();
    }

    @Test
    void nullArgumentsRejected() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        assertThatNullPointerException().isThrownBy(() -> new SamplingTraceStore(null, t -> true));
        assertThatNullPointerException().isThrownBy(() -> new SamplingTraceStore(delegate, null));
    }

    @Test
    void failedOnlyStrategyKeepsFailedTracesOnly() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        SamplingTraceStore store = new SamplingTraceStore(delegate, SamplingTraceStore.failedOnly());

        store.save(trace("ok", Status.COMPLETED, Duration.ofMillis(1)));
        store.save(trace("bad", Status.FAILED, Duration.ofMillis(1)));
        store.save(trace("halt", Status.HALTED, Duration.ofMillis(1)));
        store.save(trace("paused", Status.INTERRUPTED, Duration.ofMillis(1)));

        assertThat(delegate.listIds()).containsExactly("bad");
    }

    @Test
    void slowExecutionsStrategyKeepsTracesAtOrAboveThreshold() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        SamplingTraceStore store = new SamplingTraceStore(
                delegate, SamplingTraceStore.slowExecutions(Duration.ofMillis(100)));

        store.save(trace("fast", Status.COMPLETED, Duration.ofMillis(50)));
        store.save(trace("border", Status.COMPLETED, Duration.ofMillis(100)));
        store.save(trace("slow", Status.COMPLETED, Duration.ofMillis(500)));

        assertThat(delegate.listIds()).containsExactlyInAnyOrder("border", "slow");
    }

    @Test
    void randomStrategyAtRateZeroKeepsNothing() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        SamplingTraceStore store = new SamplingTraceStore(delegate, SamplingTraceStore.random(0.0));

        for (int i = 0; i < 100; i++) {
            store.save(trace("e" + i, Status.COMPLETED, Duration.ofMillis(1)));
        }

        assertThat(delegate.listIds()).isEmpty();
    }

    @Test
    void randomStrategyAtRateOneKeepsAll() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        SamplingTraceStore store = new SamplingTraceStore(delegate, SamplingTraceStore.random(1.0));

        for (int i = 0; i < 50; i++) {
            store.save(trace("e" + i, Status.COMPLETED, Duration.ofMillis(1)));
        }

        assertThat(delegate.listIds()).hasSize(50);
    }

    @Test
    void randomStrategyRejectsOutOfRangeRates() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SamplingTraceStore.random(-0.1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SamplingTraceStore.random(1.5));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SamplingTraceStore.random(Double.NaN));
    }

    @Test
    void slowExecutionsRejectsNullOrNegativeThreshold() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> SamplingTraceStore.slowExecutions(null));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SamplingTraceStore.slowExecutions(Duration.ofMillis(-1)));
    }

    @Test
    void deterministicStrategyIsCalledOncePerSave() {
        InMemoryTraceStore delegate = new InMemoryTraceStore();
        AtomicInteger calls = new AtomicInteger();
        SamplingTraceStore.SampleStrategy strategy = t -> {
            calls.incrementAndGet();
            return true;
        };
        SamplingTraceStore store = new SamplingTraceStore(delegate, strategy);

        store.save(trace("e1", Status.COMPLETED, Duration.ofMillis(1)));
        store.save(trace("e2", Status.COMPLETED, Duration.ofMillis(1)));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(delegate.listIds()).hasSize(2);
    }
}
