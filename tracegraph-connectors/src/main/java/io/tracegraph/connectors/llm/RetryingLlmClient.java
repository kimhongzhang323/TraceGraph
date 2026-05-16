package io.tracegraph.connectors.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decorator that wraps any {@link LlmClient} with automatic retry on transient failures.
 *
 * <p>Retries on:
 * <ul>
 *   <li>HTTP 429 (rate limit) — waits for the configured backoff before re-trying</li>
 *   <li>HTTP 5xx (server error) — retries up to {@code maxAttempts}</li>
 *   <li>{@link java.io.UncheckedIOException} / network failures</li>
 * </ul>
 *
 * <p>Backoff is exponential with full jitter: {@code random(0, base * 2^attempt)}, capped at
 * {@link #maxDelay}. This is the AWS "full jitter" strategy which prevents thundering-herd on
 * shared rate limits.
 *
 * <pre>{@code
 * LlmClient client = RetryingLlmClient.wrap(openAiClient)
 *     .maxAttempts(5)
 *     .baseDelay(Duration.ofMillis(500))
 *     .maxDelay(Duration.ofSeconds(30))
 *     .build();
 * }</pre>
 */
public final class RetryingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;

    private RetryingLlmClient(Builder b) {
        this.delegate = b.delegate;
        this.maxAttempts = b.maxAttempts;
        this.baseDelay = b.baseDelay;
        this.maxDelay = b.maxDelay;
    }

    public static Builder wrap(LlmClient delegate) {
        return new Builder(delegate);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return delegate.complete(request);
            } catch (LlmHttpException e) {
                last = e;
                if (!isRetryable(e)) throw e;
            } catch (java.io.UncheckedIOException e) {
                last = e;
            }
            if (attempt < maxAttempts - 1) {
                sleep(jitteredDelay(attempt));
            }
        }
        throw last;
    }

    @Override
    public Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) {
        return delegate.stream(request);
    }

    private boolean isRetryable(LlmHttpException e) {
        int code = e.statusCode();
        return code == 429 || (code >= 500 && code < 600);
    }

    private long jitteredDelay(int attempt) {
        long cap = Math.min(maxDelay.toMillis(),
                baseDelay.toMillis() * (1L << Math.min(attempt, 30)));
        return (long) (ThreadLocalRandom.current().nextDouble() * cap);
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", e);
        }
    }

    public static final class Builder {
        private final LlmClient delegate;
        private int maxAttempts = 4;
        private Duration baseDelay = Duration.ofMillis(500);
        private Duration maxDelay = Duration.ofSeconds(30);

        private Builder(LlmClient delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        public Builder maxAttempts(int n) {
            if (n < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            this.maxAttempts = n;
            return this;
        }

        public Builder baseDelay(Duration d) {
            this.baseDelay = Objects.requireNonNull(d, "baseDelay");
            return this;
        }

        public Builder maxDelay(Duration d) {
            this.maxDelay = Objects.requireNonNull(d, "maxDelay");
            return this;
        }

        public RetryingLlmClient build() {
            return new RetryingLlmClient(this);
        }
    }
}
