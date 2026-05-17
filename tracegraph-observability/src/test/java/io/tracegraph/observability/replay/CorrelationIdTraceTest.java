package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTraceTest {

    @Test
    void recordedTraceCarriesCorrelationIdFromBuilderSupplier() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .entry("a").terminal("a")
                .correlationId(() -> "req-123")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> r = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(r.executionId()).orElseThrow();
        assertThat(trace.correlationId()).isEqualTo("req-123");
    }

    @Test
    void defaultGraphHasNullCorrelationIdOnTrace() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .entry("a").terminal("a")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> r = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(r.executionId()).orElseThrow();
        assertThat(trace.correlationId()).isNull();
    }

    @Test
    void supplierInvokedFreshlyPerRun() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        AtomicInteger seq = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .entry("a").terminal("a")
                .correlationId(() -> "corr-" + seq.incrementAndGet())
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> r1 = graph.run("s1");
        ExecutionResult<String> r2 = graph.run("s2");

        ExecutionTrace<?> t1 = store.load(r1.executionId()).orElseThrow();
        ExecutionTrace<?> t2 = store.load(r2.executionId()).orElseThrow();
        assertThat(t1.correlationId()).isEqualTo("corr-1");
        assertThat(t2.correlationId()).isEqualTo("corr-2");
    }

    @Test
    void correlationIdRoundTripsThroughJsonFileTraceStore(@TempDir Path tmp) {
        JsonFileTraceStore<String> store = JsonFileTraceStore.of(tmp, String.class);
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .entry("a").terminal("a")
                .correlationId(() -> "json-corr-9")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> r = graph.run("seed");

        ExecutionTrace<?> reloaded = store.load(r.executionId()).orElseThrow();
        assertThat(reloaded.correlationId()).isEqualTo("json-corr-9");
    }
}
