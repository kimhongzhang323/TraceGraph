package io.tracegraph.core;

import io.tracegraph.core.spi.TraceRecorder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CorrelationIdTest {

    @Test
    void correlationIdSupplierIsInvokedOncePerRunAndForwardedToTraceRecorder() {
        AtomicInteger supplierCalls = new AtomicInteger();
        AtomicReference<String> captured = new AtomicReference<>();
        TraceRecorder recorder = new TraceRecorder() {
            @Override
            public void recordStart(String executionId, Object initialState, String correlationId) {
                captured.set(correlationId);
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .correlationId(() -> {
                    supplierCalls.incrementAndGet();
                    return "trace-parent-abc";
                })
                .traceRecorder(recorder)
                .build();

        graph.run("seed");

        assertThat(captured.get()).isEqualTo("trace-parent-abc");
        assertThat(supplierCalls.get()).isEqualTo(1);
    }

    @Test
    void defaultCorrelationIdSupplierReturnsNull() {
        AtomicReference<String> captured = new AtomicReference<>("not-null");
        TraceRecorder recorder = new TraceRecorder() {
            @Override
            public void recordStart(String executionId, Object initialState, String correlationId) {
                captured.set(correlationId);
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .traceRecorder(recorder)
                .build();

        graph.run("seed");

        assertThat(captured.get()).isNull();
    }

    @Test
    void nullSupplierIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> Graph.<String>builder().correlationId(null));
    }

    @Test
    void traceRecorderDefault3ArgRecordStartDelegatesTo2ArgForBackCompat() {
        AtomicReference<String> twoArgCalledWith = new AtomicReference<>();
        TraceRecorder onlyOverridesTwoArg = new TraceRecorder() {
            @Override
            public void recordStart(String executionId, Object initialState) {
                twoArgCalledWith.set(executionId);
            }
        };

        onlyOverridesTwoArg.recordStart("eid-1", "state", "ignored-correlation");

        assertThat(twoArgCalledWith.get()).isEqualTo("eid-1");
    }
}
