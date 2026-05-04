package io.tracegraph.core.exec;

import io.tracegraph.core.BackoffStrategy;

import java.time.Duration;

public final class BackoffFactories {

    private BackoffFactories() {}

    public static BackoffStrategy none() {
        return NoBackoff.INSTANCE;
    }

    public static BackoffStrategy fixed(Duration delay) {
        return new FixedBackoff(delay);
    }

    public static BackoffStrategy exponential(Duration initial, double multiplier, Duration cap) {
        return new ExponentialBackoff(initial, multiplier, cap);
    }
}
