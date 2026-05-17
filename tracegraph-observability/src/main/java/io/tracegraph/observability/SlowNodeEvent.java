package io.tracegraph.observability;

import java.time.Duration;
import java.util.Objects;

/**
 * Notification payload emitted by {@link SlowNodeListener} when a node's wall-clock duration
 * exceeds its configured budget. Pure data record; consumers decide whether to log, alert, or
 * publish a metric.
 *
 * @param nodeName the graph node that breached its budget
 * @param duration the observed wall-clock duration of the node execution
 * @param budget   the configured budget that was breached (per-node entry, or the default)
 */
public record SlowNodeEvent(String nodeName, Duration duration, Duration budget) {

    public SlowNodeEvent {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(budget, "budget");
    }
}
