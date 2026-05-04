package io.tracegraph.core.spi;

import io.tracegraph.core.Status;
import io.tracegraph.core.exec.NoopTraceRecorderAccess;

/**
 * ExecutionId-aware capture SPI fired alongside {@link NodeListener} but with run-level bookends
 * ({@code recordStart}/{@code recordComplete}) and the executionId on every callback. Designed
 * for replay-shaped consumers that need to demultiplex concurrent runs into separate traces.
 *
 * <p>Plug in via {@code Graph.Builder.traceRecorder(...)}; default is {@link #noop()}. Branches
 * inside {@code parallel(...)} fire one enter/exit pair on the parallel node, not per branch.
 * Implementations must be thread-safe. {@code tracegraph-observability} ships
 * {@code RecordingTraceRecorder} bridging this SPI to a {@code TraceStore}.
 */
public interface TraceRecorder {

    default void recordStart(String executionId, Object initialState) {}

    default void recordEnter(String executionId, String nodeName, int attempt, Object state) {}

    default void recordRetry(String executionId, String nodeName, int attempt, Throwable error) {}

    default void recordExit(String executionId, String nodeName, int attempts,
                            Object before, Object after, long durationNanos) {}

    default void recordExitWithChildren(String executionId, String nodeName, int attempts,
                                        Object before, Object after, long durationNanos,
                                        java.util.List<?> childrenSteps) {
        recordExit(executionId, nodeName, attempts, before, after, durationNanos);
    }

    default void recordError(String executionId, String nodeName, Throwable error) {}

    default void recordComplete(String executionId, Status status, Object finalState) {}

    static TraceRecorder noop() {
        return NoopTraceRecorderAccess.instance();
    }
}
