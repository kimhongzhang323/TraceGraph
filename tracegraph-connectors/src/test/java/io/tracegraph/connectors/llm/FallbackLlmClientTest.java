package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackLlmClientTest {

    private static LlmRequest request(String text) {
        return LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user(text)))
                .build();
    }

    private static LlmResponse response(String text) {
        return new LlmResponse(text, LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
    }

    private static LlmClient failingWith(RuntimeException failure) {
        return new MockLlmClient(r -> {
            throw failure;
        });
    }

    @Test
    void servesFromFirstClientWithoutTouchingOthers() {
        AtomicInteger secondCalls = new AtomicInteger();
        LlmClient second = new MockLlmClient(r -> {
            secondCalls.incrementAndGet();
            return response("second");
        });

        FallbackLlmClient client = FallbackLlmClient.of(MockLlmClient.constant("first"), second);

        assertThat(client.complete(request("q")).content()).isEqualTo("first");
        assertThat(secondCalls.get()).isZero();
    }

    @Test
    void fallsThroughOnRetryableHttpStatus() {
        FallbackLlmClient client = FallbackLlmClient.of(
                failingWith(new LlmHttpException(429, "rate limited")),
                failingWith(new LlmHttpException(503, "overloaded")),
                MockLlmClient.constant("third"));

        assertThat(client.complete(request("q")).content()).isEqualTo("third");
    }

    @Test
    void fallsThroughOnTimeout() {
        FallbackLlmClient client = FallbackLlmClient.of(
                failingWith(new UncheckedIOException("timed out", new IOException("timeout"))),
                MockLlmClient.constant("second"));

        assertThat(client.complete(request("q")).content()).isEqualTo("second");
    }

    @Test
    void nonRetryableHttpStatusPropagatesImmediately() {
        AtomicInteger secondCalls = new AtomicInteger();
        LlmClient second = new MockLlmClient(r -> {
            secondCalls.incrementAndGet();
            return response("second");
        });

        FallbackLlmClient client = FallbackLlmClient.of(
                failingWith(new LlmHttpException(401, "bad key")), second);

        assertThatThrownBy(() -> client.complete(request("q")))
                .isInstanceOf(LlmHttpException.class)
                .hasMessageContaining("401");
        assertThat(secondCalls.get()).isZero();
    }

    @Test
    void lastFailurePropagatesWithEarlierFailuresSuppressed() {
        LlmHttpException first = new LlmHttpException(429, "rate limited");
        LlmHttpException second = new LlmHttpException(503, "overloaded");

        FallbackLlmClient client = FallbackLlmClient.of(failingWith(first), failingWith(second));

        assertThatThrownBy(() -> client.complete(request("q")))
                .isSameAs(second)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(first));
    }

    @Test
    void customFailoverPredicateOverridesDefault() {
        FallbackLlmClient client = FallbackLlmClient.of(
                List.of(failingWith(new LlmHttpException(401, "bad key")), MockLlmClient.constant("second")),
                e -> true);

        assertThat(client.complete(request("q")).content()).isEqualTo("second");
    }

    @Test
    void interruptionNeverFailsOver() {
        AtomicInteger secondCalls = new AtomicInteger();
        LlmClient interrupted = new MockLlmClient(r -> {
            throw new RuntimeException("interrupted", new InterruptedException());
        });
        LlmClient second = new MockLlmClient(r -> {
            secondCalls.incrementAndGet();
            return response("second");
        });

        FallbackLlmClient client = FallbackLlmClient.of(interrupted, second);

        assertThatThrownBy(() -> client.complete(request("q"))).hasMessageContaining("interrupted");
        assertThat(secondCalls.get()).isZero();
    }

    @Test
    void systemNameReflectsPrimaryClient() {
        assertThat(FallbackLlmClient.of(MockLlmClient.constant("x")).systemName())
                .isEqualTo(MockLlmClient.constant("x").systemName());
    }

    @Test
    void rejectsEmptyClientList() {
        assertThatThrownBy(() -> FallbackLlmClient.of(List.of(), e -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
