package io.tracegraph.core;

import java.util.List;
import java.util.Objects;

public sealed interface NodeResult<S> {
    S state();
    record Continue<S>(S state) implements NodeResult<S> {}
    record GoTo<S>(String nodeName, S state) implements NodeResult<S> {}

    /**
     * Fan-out: dispatch multiple {@link Send} targets in parallel, then merge.
     * <p>
     * The executor will run each send's target node with its payload concurrently,
     * then invoke the provided {@link Merger} to combine results.
     *
     * @param sends  the list of send directives (target + payload)
     * @param merger how to combine the results
     * @param state  the current state (used as the base for the merger)
     */
    record SendAll<S>(List<Send<S>> sends, Merger<S> merger, S state) implements NodeResult<S> {
        public SendAll {
            Objects.requireNonNull(sends, "sends");
            Objects.requireNonNull(merger, "merger");
            if (sends.isEmpty()) {
                throw new IllegalArgumentException("sends must not be empty");
            }
            sends = List.copyOf(sends);
        }
    }

    static <S> NodeResult<S> of(S state) { return new Continue<>(state); }
    static <S> NodeResult<S> goTo(String nodeName, S state) { return new GoTo<>(nodeName, state); }

    /**
     * Create a fan-out result that dispatches N targets in parallel and merges results.
     *
     * @param sends   list of (target-node, payload) pairs
     * @param merger  how to combine results
     * @param current the current state
     */
    static <S> NodeResult<S> sendAll(List<Send<S>> sends, Merger<S> merger, S current) {
        return new SendAll<>(sends, merger, current);
    }
}
