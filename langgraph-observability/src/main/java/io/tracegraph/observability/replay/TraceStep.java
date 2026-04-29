package io.tracegraph.observability.replay;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record TraceStep<S>(int index, String nodeName, int attempts,
                           S before, S after, Duration duration, Throwable error,
                           List<TraceStep<S>> children) {

    public TraceStep {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(duration, "duration");
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of());
    }

    public boolean failed() {
        return error != null;
    }
}
