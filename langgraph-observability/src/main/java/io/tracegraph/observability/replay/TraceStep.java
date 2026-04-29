package io.tracegraph.observability.replay;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * One node's contribution to an {@link ExecutionTrace}: the before/after state, total
 * {@link #attempts} (retries are folded in, not separate steps), wall-clock {@link #duration}, and
 * an {@link #error} if the node ultimately failed (in which case {@code after} is null and this is
 * the trace's last step).
 *
 * @param <S> the graph's state type
 */
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

    public boolean failed() {
        return error != null;
    }

    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of());
    }
}
