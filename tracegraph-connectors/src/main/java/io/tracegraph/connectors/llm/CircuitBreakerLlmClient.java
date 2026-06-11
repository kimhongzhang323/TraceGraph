package io.tracegraph.connectors.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Circuit-breaker decorator over any {@link LlmClient} — stops hammering a failing provider and
 * fails fast until it recovers.
 *
 * <p>States: <em>closed</em> (calls pass; consecutive failures counted), <em>open</em> (after
 * {@code failureThreshold} consecutive failures, calls are rejected immediately with
 * {@link CircuitOpenException} carrying the last failure as cause), <em>half-open</em> (after
 * {@code cooldown}, exactly one probe call passes — success closes the circuit, failure re-opens
 * it and restarts the cooldown; concurrent calls during the probe are rejected).
 *
 * <p>State transitions are guarded by a {@link ReentrantLock}; delegate calls happen outside the
 * lock, so the breaker never pins a carrier thread across I/O.
 */
public final class CircuitBreakerLlmClient implements LlmClient {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final LlmClient delegate;
    private final int failureThreshold;
    private final long cooldownNanos;
    private final LongSupplier nanoTime;

    private final ReentrantLock lock = new ReentrantLock();
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtNanos;
    private boolean probeInFlight;
    private RuntimeException lastFailure;

    private CircuitBreakerLlmClient(LlmClient delegate, int failureThreshold, Duration cooldown,
                                    LongSupplier nanoTime) {
        this.delegate = delegate;
        this.failureThreshold = failureThreshold;
        this.cooldownNanos = cooldown.toNanos();
        this.nanoTime = nanoTime;
    }

    /** Creates a breaker that opens after {@code failureThreshold} consecutive failures for {@code cooldown}. */
    public static CircuitBreakerLlmClient of(LlmClient delegate, int failureThreshold, Duration cooldown) {
        return of(delegate, failureThreshold, cooldown, System::nanoTime);
    }

    /** Package-visible for tests — injectable clock. */
    static CircuitBreakerLlmClient of(LlmClient delegate, int failureThreshold, Duration cooldown,
                                      LongSupplier nanoTime) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        if (cooldown.isZero() || cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        return new CircuitBreakerLlmClient(delegate, failureThreshold, cooldown, nanoTime);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        boolean probing = admit();
        try {
            LlmResponse response = delegate.complete(request);
            onSuccess();
            return response;
        } catch (RuntimeException e) {
            onFailure(e, probing);
            throw e;
        }
    }

    @Override
    public String systemName() {
        return delegate.systemName();
    }

    private boolean admit() {
        lock.lock();
        try {
            switch (state) {
                case CLOSED -> {
                    return false;
                }
                case OPEN -> {
                    if (nanoTime.getAsLong() - openedAtNanos < cooldownNanos) {
                        throw rejection();
                    }
                    state = State.HALF_OPEN;
                    probeInFlight = true;
                    return true;
                }
                case HALF_OPEN -> {
                    if (probeInFlight) {
                        throw rejection();
                    }
                    probeInFlight = true;
                    return true;
                }
            }
            throw new IllegalStateException("unreachable");
        } finally {
            lock.unlock();
        }
    }

    private CircuitOpenException rejection() {
        return new CircuitOpenException(
                "Circuit open after " + consecutiveFailures + " consecutive failures", lastFailure);
    }

    private void onSuccess() {
        lock.lock();
        try {
            state = State.CLOSED;
            consecutiveFailures = 0;
            probeInFlight = false;
            lastFailure = null;
        } finally {
            lock.unlock();
        }
    }

    private void onFailure(RuntimeException e, boolean probing) {
        lock.lock();
        try {
            lastFailure = e;
            if (probing) {
                state = State.OPEN;
                openedAtNanos = nanoTime.getAsLong();
                probeInFlight = false;
                return;
            }
            consecutiveFailures++;
            if (state == State.CLOSED && consecutiveFailures >= failureThreshold) {
                state = State.OPEN;
                openedAtNanos = nanoTime.getAsLong();
            }
        } finally {
            lock.unlock();
        }
    }
}
