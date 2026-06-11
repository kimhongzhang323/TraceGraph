package io.tracegraph.connectors.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token-bucket rate-limiting decorator over any {@link LlmClient} — protects provider quotas
 * from bursty graph executions.
 *
 * <p>The bucket holds {@code permits} tokens and refills continuously at
 * {@code permits / per}. Each {@link #complete} acquires one token, blocking until one is
 * available. Blocking uses {@link ReentrantLock}/{@link Condition} (never {@code synchronized}
 * over I/O, no busy-spin), so blocked callers park virtual-thread-friendly. Interruption while
 * waiting propagates as a {@link RuntimeException} with the interrupt flag restored.
 *
 * <p>Thread-safe; one instance guards one bucket — share it across everything that must respect
 * the same quota.
 */
public final class RateLimitedLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final double capacity;
    private final double refillPerNano;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition tokenAvailable = lock.newCondition();
    private double tokens;
    private long lastRefillNanos;

    private RateLimitedLlmClient(LlmClient delegate, int permits, Duration per) {
        this.delegate = delegate;
        this.capacity = permits;
        this.refillPerNano = (double) permits / per.toNanos();
        this.tokens = permits;
        this.lastRefillNanos = System.nanoTime();
    }

    /** Creates a limiter allowing {@code permits} calls per {@code per}, with burst up to {@code permits}. */
    public static RateLimitedLlmClient of(LlmClient delegate, int permits, Duration per) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(per, "per");
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
        if (per.isZero() || per.isNegative()) {
            throw new IllegalArgumentException("per must be positive");
        }
        return new RateLimitedLlmClient(delegate, permits, per);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        acquire();
        return delegate.complete(request);
    }

    @Override
    public String systemName() {
        return delegate.systemName();
    }

    private void acquire() {
        lock.lock();
        try {
            refill();
            while (tokens < 1.0) {
                long nanosToNextToken = (long) Math.ceil((1.0 - tokens) / refillPerNano);
                tokenAvailable.awaitNanos(nanosToNextToken);
                refill();
            }
            tokens -= 1.0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for a rate-limit token", e);
        } finally {
            lock.unlock();
        }
    }

    private void refill() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
        lastRefillNanos = now;
    }
}
