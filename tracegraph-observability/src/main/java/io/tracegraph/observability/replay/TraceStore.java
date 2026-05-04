package io.tracegraph.observability.replay;

import java.util.List;
import java.util.Optional;

/**
 * Persistence SPI for {@link ExecutionTrace}s — the read-side substrate for replay. Mirrors the
 * shape of {@code CheckpointStore} (save / load / delete) but holds the full history per
 * executionId rather than just the latest checkpoint.
 *
 * <p>{@link #listIds()} is optional (default empty) so existing implementations keep compiling.
 * Implementations must be thread-safe. Ships {@code InMemoryTraceStore}, {@code JsonFileTraceStore},
 * and {@code JdbcTraceStore} in this module.
 */
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
