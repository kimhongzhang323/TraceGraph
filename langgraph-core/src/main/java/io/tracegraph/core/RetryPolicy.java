package io.tracegraph.core;

import io.tracegraph.core.exec.BackoffFactories;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

public record RetryPolicy(int maxAttempts, BackoffStrategy backoff, Predicate<Throwable> retryOn) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Objects.requireNonNull(backoff, "backoff");
        Objects.requireNonNull(retryOn, "retryOn");
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, BackoffFactories.none(), t -> false);
    }

    public static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new RetryPolicy(maxAttempts, BackoffFactories.fixed(delay), t -> true);
    }

    public static RetryPolicy exponential(int maxAttempts, Duration initial, double multiplier, Duration cap) {
        return new RetryPolicy(maxAttempts, BackoffFactories.exponential(initial, multiplier, cap), t -> true);
    }

    public RetryPolicy retryOn(Predicate<Throwable> predicate) {
        return new RetryPolicy(maxAttempts, backoff, predicate);
    }

    public boolean shouldRetry(int attempt, Throwable error) {
        if (attempt >= maxAttempts) return false;
        if (error instanceof InterruptedException) return false;
        if (error instanceof Error) return false;
        return retryOn.test(error);
    }
}
