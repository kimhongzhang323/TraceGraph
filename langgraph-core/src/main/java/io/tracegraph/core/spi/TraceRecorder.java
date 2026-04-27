package io.tracegraph.core.spi;

import io.tracegraph.core.Status;
import io.tracegraph.core.exec.NoopTraceRecorderAccess;

public interface TraceRecorder {

    default void recordStart(String executionId, Object initialState) {}

    default void recordEnter(String executionId, String nodeName, int attempt, Object state) {}

    default void recordRetry(String executionId, String nodeName, int attempt, Throwable error) {}

    default void recordExit(String executionId, String nodeName, int attempts,
                            Object before, Object after, long durationNanos) {}

    default void recordError(String executionId, String nodeName, Throwable error) {}

    default void recordComplete(String executionId, Status status, Object finalState) {}

    static TraceRecorder noop() {
        return NoopTraceRecorderAccess.instance();
    }
}
