package io.tracegraph.core;

import java.util.function.Predicate;

/**
 * A directed transition between two nodes in a {@link Graph}. The optional {@code condition}
 * predicate gates the edge; a {@code null} predicate is an unconditional fall-through. Edge
 * predicates must be pure functions of state — the executor re-evaluates them on resume.
 *
 * @param <S> the graph's state type
 */
public record Edge<S>(String from, String to, Predicate<S> condition) {

    public boolean isUnconditional() {
        return condition == null;
    }

    public boolean matches(S state) {
        return condition == null || condition.test(state);
    }
}
