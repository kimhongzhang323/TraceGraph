package io.tracegraph.core;

import java.util.Objects;

/**
 * A directive to dispatch a node with a specific payload, used for dynamic fan-out.
 * <p>
 * When a node returns a {@link NodeResult.SendAll}, the executor spawns N parallel
 * executions of the same target node with different payloads, then merges the results.
 *
 * @param target  the node name to dispatch to
 * @param payload the state to seed the target node with
 * @param <S>     the state type
 */
public record Send<S>(String target, S payload) {

    public Send {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(payload, "payload");
    }

    /** Convenience factory. */
    public static <S> Send<S> to(String target, S payload) {
        return new Send<>(target, payload);
    }
}
