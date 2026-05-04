package io.tracegraph.core.exec;

import io.tracegraph.core.BackoffStrategy;

import java.time.Duration;
import java.util.Objects;

final class ExponentialBackoff implements BackoffStrategy {

    private final Duration initial;
    private final double multiplier;
    private final Duration cap;

    ExponentialBackoff(Duration initial, double multiplier, Duration cap) {
        this.initial = Objects.requireNonNull(initial, "initial");
        this.cap = Objects.requireNonNull(cap, "cap");
        if (initial.isNegative() || initial.isZero()) {
            throw new IllegalArgumentException("initial must be > 0");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        if (cap.compareTo(initial) < 0) {
            throw new IllegalArgumentException("cap must be >= initial");
        }
        this.multiplier = multiplier;
    }

    @Override
    public Duration delayFor(int attempt) {
        if (attempt <= 1) {
            return initial;
        }
        double factor = Math.pow(multiplier, attempt - 1);
        long nanos;
        try {
            nanos = (long) Math.min((double) cap.toNanos(), initial.toNanos() * factor);
        } catch (ArithmeticException overflow) {
            return cap;
        }
        Duration computed = Duration.ofNanos(nanos);
        return computed.compareTo(cap) > 0 ? cap : computed;
    }
}
