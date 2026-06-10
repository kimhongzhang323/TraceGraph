package io.tracegraph.core;

public enum Status {
    /**
     * Execution is still in flight. Never returned by {@code Graph.run}; appears only on
     * partial traces flushed mid-run for crash durability.
     */
    RUNNING,
    COMPLETED,
    FAILED,
    INTERRUPTED,
    HALTED,
    /**
     * Execution was halted cleanly by a {@code NodeListener} throwing
     * {@link io.tracegraph.core.spi.TerminationSignalException} — distinct from
     * {@link #FAILED} (uncaught exception) and {@link #HALTED} (max-step guard).
     */
    TERMINATED
}
