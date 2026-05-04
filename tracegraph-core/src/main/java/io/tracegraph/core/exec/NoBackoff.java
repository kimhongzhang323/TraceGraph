package io.tracegraph.core.exec;

import io.tracegraph.core.BackoffStrategy;

import java.time.Duration;

final class NoBackoff implements BackoffStrategy {

    static final NoBackoff INSTANCE = new NoBackoff();

    private NoBackoff() {}

    @Override
    public Duration delayFor(int attempt) {
        return Duration.ZERO;
    }
}
