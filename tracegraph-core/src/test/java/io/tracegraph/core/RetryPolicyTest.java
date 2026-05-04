package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void noneShortCircuitsRetries() {
        RetryPolicy p = RetryPolicy.none();
        assertThat(p.maxAttempts()).isEqualTo(1);
        assertThat(p.shouldRetry(1, new RuntimeException())).isFalse();
    }

    @Test
    void fixedRetriesAnyThrowableUpToMax() {
        RetryPolicy p = RetryPolicy.fixed(3, Duration.ofMillis(1));
        assertThat(p.shouldRetry(1, new RuntimeException())).isTrue();
        assertThat(p.shouldRetry(2, new RuntimeException())).isTrue();
        assertThat(p.shouldRetry(3, new RuntimeException())).isFalse();
    }

    @Test
    void exponentialRetriesAnyThrowable() {
        RetryPolicy p = RetryPolicy.exponential(2, Duration.ofMillis(1), 2.0, Duration.ofSeconds(1));
        assertThat(p.shouldRetry(1, new IOException())).isTrue();
        assertThat(p.shouldRetry(2, new IOException())).isFalse();
    }

    @Test
    void retryOnFiltersByThrowableType() {
        RetryPolicy p = RetryPolicy.fixed(5, Duration.ofMillis(1))
                .retryOn(t -> t instanceof IOException);
        assertThat(p.shouldRetry(1, new IOException())).isTrue();
        assertThat(p.shouldRetry(1, new IllegalStateException())).isFalse();
    }

    @Test
    void neverRetriesError() {
        RetryPolicy p = RetryPolicy.fixed(5, Duration.ofMillis(1));
        assertThat(p.shouldRetry(1, new OutOfMemoryError())).isFalse();
    }

    @Test
    void neverRetriesInterruptedException() {
        RetryPolicy p = RetryPolicy.fixed(5, Duration.ofMillis(1));
        assertThat(p.shouldRetry(1, new InterruptedException())).isFalse();
    }

    @Test
    void rejectsZeroOrNegativeAttempts() {
        assertThatThrownBy(() -> RetryPolicy.fixed(0, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
