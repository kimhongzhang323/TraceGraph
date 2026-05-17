package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFileTraceStoreTest {

    public record Order(String id, int total) {}

    @Test
    void savesAndLoadsCompletedTrace(@TempDir Path tmp) {
        JsonFileTraceStore<Order> store = JsonFileTraceStore.of(tmp, Order.class);
        Graph<Order> g = Graph.<Order>builder()
                .node("price", (s, ctx) -> new Order(s.id(), s.total() + 10))
                .node("tax",   (s, ctx) -> new Order(s.id(), s.total() + 1))
                .entry("price").edge("price", "tax").terminal("tax")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<Order> r = g.run(new Order("o1", 100));

        Optional<ExecutionTrace<?>> loaded = store.load(r.executionId());
        assertThat(loaded).isPresent();
        @SuppressWarnings("unchecked")
        ExecutionTrace<Order> trace = (ExecutionTrace<Order>) loaded.get();
        assertThat(trace.status()).isEqualTo(Status.COMPLETED);
        assertThat(trace.initialState()).isEqualTo(new Order("o1", 100));
        assertThat(trace.finalState()).isEqualTo(new Order("o1", 111));
        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.steps().get(1).nodeName()).isEqualTo("tax");
    }

    @Test
    void loadReturnsEmptyForUnknownId(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        assertThat(store.load("missing")).isEmpty();
    }

    @Test
    void deleteRemovesFile(@TempDir Path tmp) throws Exception {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> r = g.run("seed");
        assertThat(Files.list(tmp).count()).isEqualTo(1);

        store.delete(r.executionId());
        assertThat(Files.list(tmp).count()).isEqualTo(0);
        store.delete(r.executionId());
        assertThat(Files.list(tmp).count()).isEqualTo(0);
    }

    @Test
    void roundTripsFailedTraceWithErrorMessage(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        Graph<String> g = Graph.<String>builder()
                .node("ok", (s, ctx) -> s + ".ok")
                .node("boom", (s, ctx) -> { throw new IllegalStateException("kaboom"); })
                .entry("ok").edge("ok", "boom").terminal("boom")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> r = g.run("");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(r.executionId()).orElseThrow();
        assertThat(trace.status()).isEqualTo(Status.FAILED);
        assertThat(trace.steps()).hasSize(2);
        TraceStep<String> failed = trace.steps().get(1);
        assertThat(failed.failed()).isTrue();
        assertThat(failed.error().getMessage()).contains("IllegalStateException", "kaboom");
    }

    @Test
    void roundTripsForkLineage(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a").edge("a", "b").terminal("b")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> first = g.run("seed");
        @SuppressWarnings("unchecked")
        ExecutionTrace<String> parent = (ExecutionTrace<String>) store.load(first.executionId()).orElseThrow();

        ExecutionResult<String> fork = ReplayRunner.of(parent, g).reRunFrom(0);
        @SuppressWarnings("unchecked")
        ExecutionTrace<String> forkTrace = (ExecutionTrace<String>) store.load(fork.executionId()).orElseThrow();

        assertThat(forkTrace.forkedFromExecutionId()).isEqualTo(first.executionId());
        assertThat(forkTrace.forkedFromStepIndex()).isEqualTo(0);
        assertThat(forkTrace.isFork()).isTrue();
    }

    @Test
    void roundTripsParentLineageForSubgraphChild(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        Graph<String> inner = Graph.<String>builder()
                .node("i", (s, ctx) -> s + ".i").entry("i").terminal("i")
                .traceRecorder(recorder)
                .build();
        Graph<String> outer = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .subgraph("sub", inner)
                .entry("a").edge("a", "sub").terminal("sub")
                .traceRecorder(recorder)
                .build();

        ExecutionResult<String> r = outer.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> childTrace =
                (ExecutionTrace<String>) store.load(r.executionId() + ":sub").orElseThrow();
        assertThat(childTrace.isChild()).isTrue();
        assertThat(childTrace.parentExecutionId()).isEqualTo(r.executionId());
        assertThat(childTrace.parentStepIndex()).isEqualTo(1);
    }

    @Test
    void listIdsReturnsAllPersistedIds(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r1 = g.run("a");
        ExecutionResult<String> r2 = g.run("b");

        assertThat(store.listIds()).containsExactlyInAnyOrder(r1.executionId(), r2.executionId());
    }

    @Test
    void rejectsExecutionIdWithPathTraversal(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        assertThatThrownBy(() -> store.load("../escape")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete("a/b")).isInstanceOf(IllegalArgumentException.class);
    }
}
