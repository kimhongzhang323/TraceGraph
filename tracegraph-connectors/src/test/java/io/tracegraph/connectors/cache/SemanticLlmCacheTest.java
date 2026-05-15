package io.tracegraph.connectors.cache;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticLlmCacheTest {

    private static final LlmRequest REQ = new LlmRequest(
            List.of(ChatMessage.user("what is Java?")), "gpt-4o", 0.5, 256);
    private static final LlmResponse RESP = new LlmResponse(
            "Java is a programming language.", LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);

    /** Embedding that always returns the same unit vector — perfect similarity. */
    private static float[] unit() { return new float[]{1.0f, 0.0f}; }

    /** Returns unit() for every text — simulates identical semantic meaning. */
    private static io.tracegraph.core.spi.EmbeddingClient identicalEmbedder() {
        return texts -> texts.stream().map(t -> unit()).toList();
    }

    @Test
    void returnsCachedResponseOnSecondCall() {
        AtomicInteger calls = new AtomicInteger();
        MockLlmClient delegate = new MockLlmClient(req -> { calls.incrementAndGet(); return RESP; });
        SemanticLlmCache cache = SemanticLlmCache.wrap(delegate)
                .embeddingClient(identicalEmbedder())
                .similarityThreshold(0.9)
                .build();

        LlmResponse first = cache.complete(REQ);
        LlmResponse second = cache.complete(REQ);

        assertThat(first).isEqualTo(RESP);
        assertThat(second).isEqualTo(RESP);
        assertThat(calls.get()).isEqualTo(1); // delegate called only once
    }

    @Test
    void callsDelegateOnCacheMiss() {
        // Each call returns a completely different vector so similarity < threshold
        AtomicInteger embedCalls = new AtomicInteger();
        AtomicInteger llmCalls = new AtomicInteger();
        MockLlmClient delegate = new MockLlmClient(req -> { llmCalls.incrementAndGet(); return RESP; });

        LlmRequest req2 = new LlmRequest(
                List.of(ChatMessage.user("explain machine learning")), "gpt-4o", 0.5, 256);

        // First request embeds to [1,0], second to [0,1] — orthogonal, similarity = 0
        SemanticLlmCache cache = SemanticLlmCache.wrap(delegate)
                .embeddingClient(texts -> {
                    int call = embedCalls.getAndIncrement();
                    return texts.stream()
                            .map(t -> call == 0 ? new float[]{1f, 0f} : new float[]{0f, 1f})
                            .toList();
                })
                .similarityThreshold(0.9)
                .build();

        cache.complete(REQ);   // stores [1,0]
        cache.complete(req2);  // queries with [0,1] — orthogonal, miss

        assertThat(llmCalls.get()).isEqualTo(2);
    }

    @Test
    void clearResetsCache() {
        AtomicInteger calls = new AtomicInteger();
        MockLlmClient delegate = new MockLlmClient(req -> { calls.incrementAndGet(); return RESP; });
        SemanticLlmCache cache = SemanticLlmCache.wrap(delegate)
                .embeddingClient(identicalEmbedder())
                .similarityThreshold(0.9)
                .build();

        cache.complete(REQ);
        cache.clear();
        cache.complete(REQ);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void respectsMaxSize() {
        MockLlmClient delegate = new MockLlmClient(req -> RESP);
        SemanticLlmCache cache = SemanticLlmCache.wrap(delegate)
                .embeddingClient(identicalEmbedder())
                .maxSize(2)
                .build();

        for (int i = 0; i < 5; i++) cache.complete(REQ);
        assertThat(cache.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void requiresEmbeddingClient() {
        assertThatThrownBy(() -> SemanticLlmCache.wrap(MockLlmClient.echoing()).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> SemanticLlmCache.wrap(MockLlmClient.echoing())
                .embeddingClient(identicalEmbedder())
                .similarityThreshold(1.5)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
