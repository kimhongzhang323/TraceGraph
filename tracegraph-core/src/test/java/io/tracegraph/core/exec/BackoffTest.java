package io.tracegraph.core.exec;

import io.tracegraph.core.BackoffStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffTest {

    @Test
    void noBackoffAlwaysZero() {
        BackoffStrategy s = BackoffFactories.none();
        assertThat(s.delayFor(1)).isEqualTo(Duration.ZERO);
        assertThat(s.delayFor(99)).isEqualTo(Duration.ZERO);
    }

    @Test
    void fixedReturnsSameDelay() {
        BackoffStrategy s = BackoffFactories.fixed(Duration.ofMillis(50));
        assertThat(s.delayFor(1)).isEqualTo(Duration.ofMillis(50));
        assertThat(s.delayFor(5)).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    void exponentialDoublesAndCaps() {
        BackoffStrategy s = BackoffFactories.exponential(
                Duration.ofMillis(100), 2.0, Duration.ofMillis(800));
        assertThat(s.delayFor(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(s.delayFor(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(s.delayFor(3)).isEqualTo(Duration.ofMillis(400));
        assertThat(s.delayFor(4)).isEqualTo(Duration.ofMillis(800));
        assertThat(s.delayFor(10)).isEqualTo(Duration.ofMillis(800));
    }

    @Test
    void exponentialRejectsBadParams() {
        assertThatThrownBy(() -> BackoffFactories.exponential(Duration.ZERO, 2.0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffFactories.exponential(Duration.ofMillis(10), 0.5, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffFactories.exponential(Duration.ofMillis(100), 2.0, Duration.ofMillis(50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixedRejectsNegative() {
        assertThatThrownBy(() -> BackoffFactories.fixed(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
