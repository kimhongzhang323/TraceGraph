package io.tracegraph.core.exec;

import io.tracegraph.core.BackoffStrategy;

import java.time.Duration;
import java.util.Objects;

final class FixedBackoff implements BackoffStrategy {

    private final Duration delay;

    FixedBackoff(Duration delay) {
        this.delay = Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must be >= 0");
        }
    }

    @Override
    public Duration delayFor(int attempt) {
        return delay;
    }
}
