package io.tracegraph.core;

public enum Status {
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
