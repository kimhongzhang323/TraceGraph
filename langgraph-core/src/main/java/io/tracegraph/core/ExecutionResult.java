package io.tracegraph.core;

import java.util.List;

public record ExecutionResult<S>(S finalState, List<String> path, Status status, Throwable error) {

    public ExecutionResult {
        path = List.copyOf(path);
    }

    public boolean isSuccess() {
        return status == Status.COMPLETED;
    }
}
