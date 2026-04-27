package io.tracegraph.observability.replay;

import java.util.Optional;

public interface TraceStore {

    void save(ExecutionTrace<?> trace);

    Optional<ExecutionTrace<?>> load(String executionId);

    void delete(String executionId);

    static TraceStore noop() {
        return new TraceStore() {
            @Override public void save(ExecutionTrace<?> trace) {}
            @Override public Optional<ExecutionTrace<?>> load(String executionId) { return Optional.empty(); }
            @Override public void delete(String executionId) {}
        };
    }
}
