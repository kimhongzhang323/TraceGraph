package io.tracegraph.core;

import java.util.function.Predicate;

public record Edge<S>(String from, String to, Predicate<S> condition) {

    public boolean isUnconditional() {
        return condition == null;
    }

    public boolean matches(S state) {
        return condition == null || condition.test(state);
    }
}
