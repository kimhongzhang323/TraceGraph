package io.tracegraph.core.spi;

/**
 * Thrown by a {@link NodeListener} (typically from {@code onExit}) to signal that the executor
 * should halt the current execution cleanly. The executor catches it and surfaces the run as
 * {@link io.tracegraph.core.Status#TERMINATED} with a null {@code error} — distinct from
 * {@link io.tracegraph.core.Status#FAILED} (uncaught exception) and
 * {@link io.tracegraph.core.Status#HALTED} (max-step guard tripped).
 *
 * <p>Intended as the shared convergence contract for multi-agent / supervisor patterns where a
 * {@code maxTurns} / {@code maxIterations} counter or a state-shape predicate decides termination
 * independently of the graph wiring. See the {@code TerminationListener} factories in
 * {@code tracegraph-observability} for built-in policies.
 */
public class TerminationSignalException extends RuntimeException {

    public TerminationSignalException(String message) {
        super(message);
    }

    public TerminationSignalException(String message, Throwable cause) {
        super(message, cause);
    }
}
