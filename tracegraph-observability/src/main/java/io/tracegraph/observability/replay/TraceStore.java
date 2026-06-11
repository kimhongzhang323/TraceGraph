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

    /**
     * Persist one newly recorded step of an in-flight trace. The default implementation saves
     * the full {@code snapshot} (so every store works unchanged); stores backed by append-friendly
     * media can override to write only {@code newStep} instead of rewriting the whole trace on
     * each periodic flush.
     *
     * @param snapshot the full in-flight trace including {@code newStep} as its last entry
     * @param newStep  the step that was just recorded
     */
    default void appendStep(ExecutionTrace<?> snapshot, TraceStep<?> newStep) {
        save(snapshot);
    }

    static TraceStore noop() {
        return new TraceStore() {
            @Override public void save(ExecutionTrace<?> trace) {}
            @Override public Optional<ExecutionTrace<?>> load(String executionId) { return Optional.empty(); }
            @Override public void delete(String executionId) {}
        };
    }
}
