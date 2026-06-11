package io.tracegraph.connectors.cache;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmHttpException;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingLlmClientTest {

    private static LlmRequest request(String text) {
        return LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user(text)))
                .build();
    }

    private static final class CountingClient {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean failing = new AtomicBoolean(false);

        MockLlmClient client() {
            return new MockLlmClient(r -> {
                int n = calls.incrementAndGet();
                if (failing.get()) {
                    throw new LlmHttpException(503, "down");
                }
                return new LlmResponse("answer-" + n, LlmResponse.FinishReason.STOP,
                        LlmResponse.Usage.ZERO);
            });
        }
    }

    @Test
    void identicalRequestIsServedFromCacheWithoutDelegateCall() {
        CountingClient counting = new CountingClient();
        CachingLlmClient client = CachingLlmClient.of(counting.client());

        LlmResponse first = client.complete(request("q"));
        LlmResponse second = client.complete(request("q"));

        assertThat(second).isSameAs(first);
        assertThat(counting.calls.get()).isEqualTo(1);
    }

    @Test
    void distinctRequestsMiss() {
        CountingClient counting = new CountingClient();
        CachingLlmClient client = CachingLlmClient.of(counting.client());

        client.complete(request("a"));
        client.complete(request("b"));

        assertThat(counting.calls.get()).isEqualTo(2);
    }

    @Test
    void failuresAreNotCached() {
        CountingClient counting = new CountingClient();
        CachingLlmClient client = CachingLlmClient.of(counting.client());
        counting.failing.set(true);

        assertThatThrownBy(() -> client.complete(request("q"))).isInstanceOf(LlmHttpException.class);
        counting.failing.set(false);

        assertThat(client.complete(request("q")).content()).isEqualTo("answer-2");
        assertThat(client.size()).isEqualTo(1);
    }

    @Test
    void evictsLeastRecentlyUsedBeyondMaxSize() {
        CountingClient counting = new CountingClient();
        CachingLlmClient client = CachingLlmClient.of(counting.client(), 2);

        client.complete(request("a"));
        client.complete(request("b"));
        client.complete(request("a"));
        client.complete(request("c"));

        assertThat(client.size()).isEqualTo(2);
        client.complete(request("a"));
        assertThat(counting.calls.get()).isEqualTo(3);
        client.complete(request("b"));
        assertThat(counting.calls.get()).isEqualTo(4);
    }

    @Test
    void rejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> CachingLlmClient.of(MockLlmClient.constant("x"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemNameDelegates() {
        assertThat(CachingLlmClient.of(MockLlmClient.constant("x")).systemName())
                .isEqualTo(MockLlmClient.constant("x").systemName());
    }
}
