package io.tracegraph.core;

public sealed interface NodeEvent<S> {
    String executionId();
    String nodeName();

    record NodeEnter<S>(String executionId, String nodeName, S before) implements NodeEvent<S> {}
    record NodeExit<S>(String executionId, String nodeName, S before, S after) implements NodeEvent<S> {}
    record NodeRetry<S>(String executionId, String nodeName, int attempt, Throwable cause) implements NodeEvent<S> {}
    record Failed<S>(String executionId, String nodeName, Throwable cause) implements NodeEvent<S> {}
    record Complete<S>(String executionId, String nodeName, ExecutionResult<S> result) implements NodeEvent<S> {}
}
