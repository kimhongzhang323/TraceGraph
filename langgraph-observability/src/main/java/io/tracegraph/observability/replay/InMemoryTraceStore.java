package io.tracegraph.observability.replay;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryTraceStore implements TraceStore {

    private final ConcurrentMap<String, ExecutionTrace<?>> traces = new ConcurrentHashMap<>();

    @Override
    public void save(ExecutionTrace<?> trace) {
        Objects.requireNonNull(trace, "trace");
        traces.put(trace.executionId(), trace);
    }

    @Override
    public Optional<ExecutionTrace<?>> load(String executionId) {
        return Optional.ofNullable(traces.get(executionId));
    }

    @Override
    public void delete(String executionId) {
        traces.remove(executionId);
    }

    public int size() {
        return traces.size();
    }
}
