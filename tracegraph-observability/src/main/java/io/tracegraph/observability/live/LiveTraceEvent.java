package io.tracegraph.observability.live;

import java.time.Instant;
import java.util.Objects;

/**
 * One live execution event broadcast by {@link LiveTraceFeed} — the wire shape behind the
 * {@code GET /tracegraph/stream} SSE endpoint.
 *
 * @param executionId execution this event belongs to
 * @param type        lifecycle stage
 * @param nodeName    node the event concerns; {@code null} for run-level START/COMPLETE
 * @param attempt     attempt number for ENTER/RETRY events; 0 otherwise
 * @param detail      human-readable payload: status for COMPLETE, error message for RETRY/ERROR,
 *                    duration in millis for EXIT; {@code null} when not applicable
 * @param timestamp   when the event was recorded
 */
public record LiveTraceEvent(String executionId, Type type, String nodeName, int attempt,
                             String detail, Instant timestamp) {

    public enum Type { START, ENTER, RETRY, EXIT, ERROR, COMPLETE }

    public LiveTraceEvent {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
