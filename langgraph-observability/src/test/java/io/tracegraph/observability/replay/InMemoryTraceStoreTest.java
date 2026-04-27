package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTraceStoreTest {

    @Test
    void roundTripsTraces() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        ExecutionTrace<String> trace = new ExecutionTrace<>("e1", "in", "out",
                Status.COMPLETED, null, List.of(), Instant.EPOCH, Instant.EPOCH);

        store.save(trace);

        assertThat(store.load("e1")).hasValue(trace);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void loadOfUnknownIdReturnsEmpty() {
        assertThat(new InMemoryTraceStore().load("missing")).isEmpty();
    }

    @Test
    void deleteRemovesTrace() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>("e1", "in", "out",
                Status.COMPLETED, null, List.of(), Instant.EPOCH, Instant.EPOCH));

        store.delete("e1");

        assertThat(store.load("e1")).isEmpty();
        assertThat(store.size()).isZero();
    }
}
