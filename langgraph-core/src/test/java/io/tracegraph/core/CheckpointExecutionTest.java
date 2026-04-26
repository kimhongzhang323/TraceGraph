package io.tracegraph.core;

import io.tracegraph.core.spi.CheckpointStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointExecutionTest {

    static final class TestStore implements CheckpointStore {
        final List<Checkpoint<?>> all = new ArrayList<>();
        final Map<String, Checkpoint<?>> latest = new ConcurrentHashMap<>();

        @Override public synchronized void save(Checkpoint<?> cp) {
            all.add(cp);
            latest.put(cp.executionId(), cp);
        }
        @Override public Optional<Checkpoint<?>> latest(String id) { return Optional.ofNullable(latest.get(id)); }
        @Override public void delete(String id) { latest.remove(id); }
    }

    @Test
    void writesCheckpointAfterEachSuccessfulNode() {
        TestStore store = new TestStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .checkpointStore(store)
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(store.all).hasSize(3);
        assertThat(store.all.stream().map(Checkpoint::lastCompletedNode)).containsExactly("a", "b", "c");
        assertThat(store.all.get(2).state()).isEqualTo(".a.b.c");
        assertThat(store.all.stream().map(Checkpoint::executionId).distinct()).hasSize(1);
    }

    @Test
    void retriesDoNotWriteCheckpoints() {
        TestStore store = new TestStore();
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 3) throw new RuntimeException("retry me");
                    return "ok";
                }, RetryPolicy.fixed(5, Duration.ofMillis(1)))
                .entry("flaky")
                .terminal("flaky")
                .checkpointStore(store)
                .build();

        graph.run("");

        assertThat(store.all).hasSize(1);
        assertThat(store.all.get(0).lastCompletedNode()).isEqualTo("flaky");
    }

    @Test
    void failureBeforeNodeExitWritesNoCheckpoint() {
        TestStore store = new TestStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("doomed", (s, ctx) -> { throw new IllegalStateException("nope"); })
                .entry("a")
                .edge("a", "doomed")
                .terminal("doomed")
                .checkpointStore(store)
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(store.all).hasSize(1);
        assertThat(store.all.get(0).lastCompletedNode()).isEqualTo("a");
    }

    @Test
    void executionIdIsStableAcrossCheckpointsAndResult() {
        TestStore store = new TestStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .entry("a")
                .terminal("a")
                .checkpointStore(store)
                .build();

        ExecutionResult<String> result = graph.run("seed", "exec-fixed");

        assertThat(result.executionId()).isEqualTo("exec-fixed");
        assertThat(store.all.get(0).executionId()).isEqualTo("exec-fixed");
    }

    @Test
    void multipleGraphsShareOneStore() {
        TestStore store = new TestStore();
        Graph<String> g1 = Graph.<String>builder()
                .node("x", (s, ctx) -> "X")
                .entry("x").terminal("x")
                .checkpointStore(store).build();
        Graph<Integer> g2 = Graph.<Integer>builder()
                .node("y", (s, ctx) -> s + 1)
                .entry("y").terminal("y")
                .checkpointStore(store).build();

        g1.run("seed", "exec-1");
        g2.run(0, "exec-2");

        assertThat(store.latest("exec-1")).isPresent();
        assertThat(store.latest("exec-2")).isPresent();
        assertThat(store.latest("exec-1").get().state()).isEqualTo("X");
        assertThat(store.latest("exec-2").get().state()).isEqualTo(1);
    }

    @Test
    void noopStoreIsDefault() {
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> "ok")
                .entry("a").terminal("a")
                .build();

        ExecutionResult<String> result = graph.run("");
        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.executionId()).isNotBlank();
    }

    @Test
    void checkpointTimestampIsRecent() {
        TestStore store = new TestStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> "ok")
                .entry("a").terminal("a")
                .checkpointStore(store).build();

        Instant before = Instant.now();
        graph.run("");
        Instant after = Instant.now();

        Instant ts = store.all.get(0).timestamp();
        assertThat(ts).isBetween(before.minusMillis(1), after.plusMillis(1));
    }
}
