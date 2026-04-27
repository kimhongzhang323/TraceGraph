package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
}
