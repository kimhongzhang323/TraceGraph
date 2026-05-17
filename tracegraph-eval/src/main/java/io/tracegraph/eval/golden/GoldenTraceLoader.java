package io.tracegraph.eval.golden;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceStore;

import java.util.List;
import java.util.Objects;

/**
 * Loads all {@link GoldenTrace}s from a {@link TraceStore}.
 * Typical usage: point at a {@code JsonFileTraceStore} holding hand-curated or
 * previously-captured runs, then feed the result to an {@link EvalRunner}.
 */
public final class GoldenTraceLoader {

    private GoldenTraceLoader() {}

    /**
     * Load every trace in {@code store} as a golden reference.
     * Relies on {@link TraceStore#listIds()} — stores that return an empty default
     * (e.g., {@code JdbcTraceStore} without an override) will yield an empty list.
     *
     * @param <S>   the graph's state type
     * @param store the source store; must not be null
     * @return immutable list of golden traces, one per id
     */
    @SuppressWarnings("unchecked")
    public static <S> List<GoldenTrace<S>> loadAll(TraceStore store) {
        Objects.requireNonNull(store, "store");
        return store.listIds().stream()
                .map(id -> store.load(id).orElseThrow(
                        () -> new IllegalStateException("trace vanished between listIds and load: " + id)))
                .map(t -> new GoldenTrace<>((ExecutionTrace<S>) t))
                .toList();
    }
}
