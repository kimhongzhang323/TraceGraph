package io.tracegraph.eval.golden;

import io.tracegraph.observability.replay.ExecutionTrace;

import java.util.Objects;

/**
 * An {@link ExecutionTrace} promoted to a golden reference for evaluation.
 * Loaded via {@link GoldenTraceLoader}; consumed by {@link EvalRunner}.
 *
 * @param <S> the graph's state type
 * @param trace the captured execution used as the expected baseline
 */
public record GoldenTrace<S>(ExecutionTrace<S> trace) {

    public GoldenTrace {
        Objects.requireNonNull(trace, "trace");
    }

    /** Convenience: executionId of the wrapped trace. */
    public String id() {
        return trace.executionId();
    }

    /** Convenience: initialState of the wrapped trace — used as the re-run seed. */
    public S seed() {
        return trace.initialState();
    }
}
