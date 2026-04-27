package io.tracegraph.observability.replay;

import java.util.List;
import java.util.Optional;

public interface TraceStore {

    void save(ExecutionTrace<?> trace);

    Optional<ExecutionTrace<?>> load(String executionId);

    void delete(String executionId);

    default List<String> listIds() {
        return List.of();
    }

    static TraceStore noop() {
        return new TraceStore() {
            @Override public void save(ExecutionTrace<?> trace) {}
            @Override public Optional<ExecutionTrace<?>> load(String executionId) { return Optional.empty(); }
            @Override public void delete(String executionId) {}
        };
    }
}
