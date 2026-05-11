package io.tracegraph.core;

import io.tracegraph.core.spi.CheckpointStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeMidParallelTest {

    static final class MapStore implements CheckpointStore {
        final Map<String, Checkpoint<?>> map = new ConcurrentHashMap<>();

        @Override public synchronized void save(Checkpoint<?> cp) { map.put(cp.executionId(), cp); }
        @Override public Optional<Checkpoint<?>> latest(String id) { return Optional.ofNullable(map.get(id)); }
        @Override public void delete(String id) { map.remove(id); }
    }

    @Test
    @Timeout(10)
    void parallelRerunsFromStartOnResume() {
        MapStore store = new MapStore();
        AtomicInteger branchXCalls = new AtomicInteger();

        Graph<String> graph = Graph.<String>builder()
                .node("nodeA", (s, ctx) -> s + ".a")
                .parallel("p", List.of(
                        (s, ctx) -> {
                            if (branchXCalls.incrementAndGet() == 1) {
                                throw new RuntimeException("first-run-fail");
                            }
                            return s + ".x";
                        },
                        (s, ctx) -> s + ".y"
                ), (input, results) -> results.get(0) + results.get(1))
                .node("nodeB", (s, ctx) -> s + ".b")
                .entry("nodeA")
                .edge("nodeA", "p")
                .edge("p", "nodeB")
                .terminal("nodeB")
                .checkpointStore(store)
                .build();

        ExecutionResult<String> first = graph.run("", "exec-parallel-1");
        assertThat(first.status()).isEqualTo(Status.FAILED);
        assertThat(store.map.get("exec-parallel-1").lastCompletedNode()).isEqualTo("nodeA");

        Optional<ExecutionResult<String>> resumed = graph.resume("exec-parallel-1");

        assertThat(resumed).isPresent();
        ExecutionResult<String> r = resumed.get();
        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.path()).contains("p", "nodeB");
        assertThat(branchXCalls.get()).isEqualTo(2);
    }
}
