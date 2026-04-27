package io.tracegraph.observability.replay;

import java.time.Duration;
import java.util.Objects;

public record TraceStep<S>(int index, String nodeName, int attempts,
                           S before, S after, Duration duration, Throwable error) {

    public TraceStep {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(duration, "duration");
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
    }

    public boolean failed() {
        return error != null;
    }
}
