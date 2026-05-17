package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Captured trace of a single execution: initial/final state, status, ordered {@link TraceStep}s,
 * timestamps, and (if produced by a {@code ReplayRunner}) lineage back to the parent trace.
 * One {@code ExecutionTrace} per executionId — resume appends to the prior trace.
 *
 * <p>Persisted via {@code TraceStore}; walked via {@code Replayer}; re-executed via {@code
 * ReplayRunner}; compared via {@link TraceDiff}.
 *
 * @param <S> the graph's state type
 */
public record ExecutionTrace<S>(String executionId, S initialState, S finalState,
                                Status status, Throwable error,
                                List<TraceStep<S>> steps,
                                Instant startedAt, Instant completedAt,
                                String forkedFromExecutionId, int forkedFromStepIndex) {

    public ExecutionTrace {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        steps = List.copyOf(steps);
    }

    public ExecutionTrace(String executionId, S initialState, S finalState,
                          Status status, Throwable error,
                          List<TraceStep<S>> steps,
                          Instant startedAt, Instant completedAt) {
        this(executionId, initialState, finalState, status, error, steps, startedAt, completedAt, null, -1);
    }

    public boolean isFork() {
        return forkedFromExecutionId != null;
    }

    public TraceStep.Usage totalUsage() {
        return steps.stream()
                .filter(s -> s.usage() != null)
                .map(TraceStep::usage)
                .reduce(TraceStep.Usage.ZERO, TraceStep.Usage::plus);
    }
}
