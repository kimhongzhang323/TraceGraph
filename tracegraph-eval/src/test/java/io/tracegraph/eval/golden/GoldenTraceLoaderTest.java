package io.tracegraph.eval.golden;

import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.TraceStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenTraceLoaderTest {

    private static ExecutionTrace<String> trace(String id, String initial, String finalSt) {
        return new ExecutionTrace<>(id, initial, finalSt, Status.COMPLETED, null,
                List.of(), Instant.now(), Instant.now());
    }

    @Test
    void loadsAllTracesFromStore() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(trace("id-1", "a", "A"));
        store.save(trace("id-2", "b", "B"));

        List<GoldenTrace<String>> goldens = GoldenTraceLoader.loadAll(store);

        assertThat(goldens).hasSize(2);
        assertThat(goldens).extracting(g -> g.trace().executionId())
                .containsExactlyInAnyOrder("id-1", "id-2");
    }

    @Test
    void returnsEmptyListWhenStoreIsEmpty() {
        InMemoryTraceStore store = new InMemoryTraceStore();

        List<GoldenTrace<String>> goldens = GoldenTraceLoader.loadAll(store);

        assertThat(goldens).isEmpty();
    }

    @Test
    void goldenTraceExposesConvenienceAccessors() {
        ExecutionTrace<String> trace = trace("exec-1", "seed", "result");
        GoldenTrace<String> golden = new GoldenTrace<>(trace);

        assertThat(golden.id()).isEqualTo("exec-1");
        assertThat(golden.seed()).isEqualTo("seed");
        assertThat(golden.trace()).isSameAs(trace);
    }

    @Test
    void rejectsNullStore() {
        assertThatThrownBy(() -> GoldenTraceLoader.loadAll((TraceStore) null))
                .isInstanceOf(NullPointerException.class);
    }
}
