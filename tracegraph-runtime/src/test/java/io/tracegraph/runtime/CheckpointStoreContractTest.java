package io.tracegraph.runtime;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.spi.CheckpointStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

abstract class CheckpointStoreContractTest {

    protected abstract CheckpointStore createStore();

    private CheckpointStore store;

    @BeforeEach
    void createStoreUnderTest() {
        store = createStore();
    }

    @Test
    void latestReturnsEmptyWhenNothingSaved() {
        assertThat(store.latest("unknown")).isEmpty();
    }

    @Test
    void saveThenLatestRoundTrips() {
        Instant t = Instant.parse("2026-04-28T10:15:30Z");
        store.save(new Checkpoint<>("exec-1", "validate", "seed.validated", t, false));

        Optional<Checkpoint<?>> loaded = store.latest("exec-1");
        assertThat(loaded).isPresent();
        Checkpoint<?> cp = loaded.get();
        assertThat(cp.executionId()).isEqualTo("exec-1");
        assertThat(cp.lastCompletedNode()).isEqualTo("validate");
        assertThat(cp.state()).isEqualTo("seed.validated");
        assertThat(cp.timestamp()).isEqualTo(t);
        assertThat(cp.interruptPending()).isFalse();
    }

    @Test
    void secondSaveForSameExecutionWins() {
        store.save(new Checkpoint<>("exec-1", "validate", "v1", Instant.parse("2026-04-28T10:00:00Z"), false));
        store.save(new Checkpoint<>("exec-1", "charge", "v2", Instant.parse("2026-04-28T11:00:00Z"), false));

        Checkpoint<?> cp = store.latest("exec-1").orElseThrow();
        assertThat(cp.lastCompletedNode()).isEqualTo("charge");
        assertThat(cp.state()).isEqualTo("v2");
    }

    @Test
    void executionsAreIsolated() {
        Instant t = Instant.parse("2026-04-28T10:00:00Z");
        store.save(new Checkpoint<>("exec-1", "a", "s1", t, false));
        store.save(new Checkpoint<>("exec-2", "b", "s2", t, false));

        assertThat(store.latest("exec-1").orElseThrow().state()).isEqualTo("s1");
        assertThat(store.latest("exec-2").orElseThrow().state()).isEqualTo("s2");
    }
}
