package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLlmClientTest {

    private static final LlmRequest REQUEST = new LlmRequest(
            List.of(ChatMessage.user("hello")), "gpt-4o", 0.5, 256);
    private static final LlmResponse OK = new LlmResponse(
            "ok", LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);

    @Test
    void returnsSuccessOnFirstAttempt() {
        LlmClient client = RetryingLlmClient.wrap(req -> OK)
                .maxAttempts(3).baseDelay(Duration.ZERO).build();
        assertThat(client.complete(REQUEST)).isEqualTo(OK);
    }

    @Test
    void retriesOn429ThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = RetryingLlmClient.wrap(req -> {
            if (calls.incrementAndGet() < 3) throw new LlmHttpException(429, "rate limited");
            return OK;
        }).maxAttempts(5).baseDelay(Duration.ZERO).build();

        LlmResponse response = client.complete(REQUEST);

        assertThat(response).isEqualTo(OK);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void retriesOn503ThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = RetryingLlmClient.wrap(req -> {
            if (calls.incrementAndGet() == 1) throw new LlmHttpException(503, "unavailable");
            return OK;
        }).maxAttempts(3).baseDelay(Duration.ZERO).build();

        assertThat(client.complete(REQUEST)).isEqualTo(OK);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void doesNotRetryOn400() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = RetryingLlmClient.wrap(req -> {
            calls.incrementAndGet();
            throw new LlmHttpException(400, "bad request");
        }).maxAttempts(5).baseDelay(Duration.ZERO).build();

        assertThatThrownBy(() -> client.complete(REQUEST))
                .isInstanceOf(LlmHttpException.class)
                .hasMessageContaining("400");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void exhaustsAttemptsAndThrowsLast() {
        LlmClient client = RetryingLlmClient.wrap(req -> {
            throw new LlmHttpException(429, "still rate limited");
        }).maxAttempts(3).baseDelay(Duration.ZERO).build();

        assertThatThrownBy(() -> client.complete(REQUEST))
                .isInstanceOf(LlmHttpException.class)
                .hasMessageContaining("429");
    }

    @Test
    void retriesOnNetworkError() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = RetryingLlmClient.wrap(req -> {
            if (calls.incrementAndGet() < 2)
                throw new UncheckedIOException(new IOException("connection reset"));
            return OK;
        }).maxAttempts(3).baseDelay(Duration.ZERO).build();

        assertThat(client.complete(REQUEST)).isEqualTo(OK);
    }

    @Test
    void rejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> RetryingLlmClient.wrap(req -> OK).maxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
