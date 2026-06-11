package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerLlmClientTest {

    private static LlmRequest request() {
        return LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user("q")))
                .build();
    }

    private static LlmResponse ok() {
        return new LlmResponse("ok", LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
    }

    private static final class Script {
        final AtomicBoolean failing = new AtomicBoolean(true);
        final AtomicInteger delegateCalls = new AtomicInteger();

        LlmClient client() {
            return new MockLlmClient(r -> {
                delegateCalls.incrementAndGet();
                if (failing.get()) {
                    throw new LlmHttpException(503, "down");
                }
                return ok();
            });
        }
    }

    @Test
    void opensAfterThresholdConsecutiveFailuresAndRejectsFast() {
        Script script = new Script();
        AtomicLong clock = new AtomicLong();
        CircuitBreakerLlmClient breaker = CircuitBreakerLlmClient.of(
                script.client(), 2, Duration.ofSeconds(30), clock::get);

        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);
        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);

        assertThatThrownBy(() -> breaker.complete(request()))
                .isInstanceOf(CircuitOpenException.class)
                .hasCauseInstanceOf(LlmHttpException.class);
        assertThat(script.delegateCalls.get()).isEqualTo(2);
    }

    @Test
    void halfOpensAfterCooldownAndClosesOnProbeSuccess() {
        Script script = new Script();
        AtomicLong clock = new AtomicLong();
        CircuitBreakerLlmClient breaker = CircuitBreakerLlmClient.of(
                script.client(), 1, Duration.ofSeconds(30), clock::get);

        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);
        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(CircuitOpenException.class);

        clock.addAndGet(Duration.ofSeconds(31).toNanos());
        script.failing.set(false);

        assertThat(breaker.complete(request()).content()).isEqualTo("ok");
        assertThat(breaker.complete(request()).content()).isEqualTo("ok");
        assertThat(script.delegateCalls.get()).isEqualTo(3);
    }

    @Test
    void probeFailureReopensAndRestartsCooldown() {
        Script script = new Script();
        AtomicLong clock = new AtomicLong();
        CircuitBreakerLlmClient breaker = CircuitBreakerLlmClient.of(
                script.client(), 1, Duration.ofSeconds(30), clock::get);

        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);
        clock.addAndGet(Duration.ofSeconds(31).toNanos());
        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);

        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(CircuitOpenException.class);
        clock.addAndGet(Duration.ofSeconds(29).toNanos());
        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(CircuitOpenException.class);

        clock.addAndGet(Duration.ofSeconds(2).toNanos());
        script.failing.set(false);
        assertThat(breaker.complete(request()).content()).isEqualTo("ok");
    }

    @Test
    void successResetsTheConsecutiveFailureCount() {
        Script script = new Script();
        CircuitBreakerLlmClient breaker = CircuitBreakerLlmClient.of(
                script.client(), 2, Duration.ofSeconds(30), () -> 0L);

        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);
        script.failing.set(false);
        assertThat(breaker.complete(request()).content()).isEqualTo("ok");
        script.failing.set(true);
        assertThatThrownBy(() -> breaker.complete(request())).isInstanceOf(LlmHttpException.class);

        // one failure after a success — still closed
        script.failing.set(false);
        assertThat(breaker.complete(request()).content()).isEqualTo("ok");
    }

    @Test
    void rejectsNonPositiveConfiguration() {
        assertThatThrownBy(() -> CircuitBreakerLlmClient.of(MockLlmClient.constant("x"), 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CircuitBreakerLlmClient.of(MockLlmClient.constant("x"), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemNameDelegates() {
        assertThat(CircuitBreakerLlmClient.of(MockLlmClient.constant("x"), 1, Duration.ofSeconds(1)).systemName())
                .isEqualTo(MockLlmClient.constant("x").systemName());
    }
}
