package io.tracegraph.core;

import java.util.List;

/**
 * The outcome of a {@link Graph} execution: the final state, the ordered path of nodes that ran,
 * the {@link Status}, and (if the run failed) the {@link Throwable} that terminated it.
 *
 * @param <S> the graph's state type
 */
public record ExecutionResult<S>(String executionId, S finalState, List<String> path, Status status, Throwable error) {

    public ExecutionResult {
        path = List.copyOf(path);
    }

    public boolean isSuccess() {
        return status == Status.COMPLETED;
    }
}
