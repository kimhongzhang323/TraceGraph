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
 * <p>Two independent lineage links may be set:
 * <ul>
 *   <li>{@code forkedFromExecutionId} / {@code forkedFromStepIndex} — populated when the trace
 *       was produced by {@code ReplayRunner} re-executing a prior trace from a chosen step.
 *   <li>{@code parentExecutionId} / {@code parentStepIndex} — populated when the trace was
 *       produced by a subgraph invocation embedded in a parent graph. Mirrors the fork lineage
 *       pattern so multi-agent / multi-graph executions can be navigated up to their parent.
 * </ul>
 * The two are orthogonal: a replay fork of a subgraph child carries both.
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
                                String forkedFromExecutionId, int forkedFromStepIndex,
                                String parentExecutionId, int parentStepIndex) {

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
        this(executionId, initialState, finalState, status, error, steps, startedAt, completedAt,
                null, -1, null, -1);
    }

    public ExecutionTrace(String executionId, S initialState, S finalState,
                          Status status, Throwable error,
                          List<TraceStep<S>> steps,
                          Instant startedAt, Instant completedAt,
                          String forkedFromExecutionId, int forkedFromStepIndex) {
        this(executionId, initialState, finalState, status, error, steps, startedAt, completedAt,
                forkedFromExecutionId, forkedFromStepIndex, null, -1);
    }

    public boolean isFork() {
        return forkedFromExecutionId != null;
    }

    public boolean isChild() {
        return parentExecutionId != null;
    }

    public TraceStep.Usage totalUsage() {
        return steps.stream()
                .filter(s -> s.usage() != null)
                .map(TraceStep::usage)
                .reduce(TraceStep.Usage.ZERO, TraceStep.Usage::plus);
    }
}
