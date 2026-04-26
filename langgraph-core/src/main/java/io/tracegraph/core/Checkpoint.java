package io.tracegraph.core;

import java.time.Instant;
import java.util.Objects;

public record Checkpoint<S>(String executionId, String lastCompletedNode, S state, Instant timestamp) {

    public Checkpoint {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(lastCompletedNode, "lastCompletedNode");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
