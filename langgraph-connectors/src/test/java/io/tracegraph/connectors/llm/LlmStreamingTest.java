package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LlmStreamingTest {

    /**
     * Blocking subscriber that collects all chunks, then completes a future
     * when onComplete or onError is called.
     */
    private static class CollectingSubscriber implements Flow.Subscriber<LlmStreamChunk> {
        final List<LlmStreamChunk> chunks = Collections.synchronizedList(new ArrayList<>());
        final CompletableFuture<List<LlmStreamChunk>> result = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }

        @Override
        public void onNext(LlmStreamChunk chunk) { chunks.add(chunk); }

        @Override
        public void onError(Throwable t) { result.completeExceptionally(t); }

        @Override
        public void onComplete() {
            // Small delay to ensure all onNext calls have been fully processed
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            result.complete(List.copyOf(chunks));
        }
    }

    @Test
    void defaultStreamWrapsCompleteInSingleChunk() throws Exception {
        MockLlmClient client = MockLlmClient.constant("hello world");
        LlmRequest req = LlmRequest.builder()
                .model("test").messages(List.of(ChatMessage.user("hi"))).build();

        CollectingSubscriber sub = new CollectingSubscriber();
        client.stream(req).subscribe(sub);
        List<LlmStreamChunk> collected = sub.result.get(5, TimeUnit.SECONDS);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).delta()).isEqualTo("hello world");
        assertThat(collected.get(0).isLast()).isTrue();
        assertThat(collected.get(0).finishReason()).isEqualTo(LlmResponse.FinishReason.STOP);
    }

    @Test
    void streamingMockEmitsWordByWord() throws Exception {
        MockLlmClient client = MockLlmClient.streaming("hello world foo");
        LlmRequest req = LlmRequest.builder()
                .model("test").messages(List.of(ChatMessage.user("hi"))).build();

        CollectingSubscriber sub = new CollectingSubscriber();
        client.stream(req).subscribe(sub);
        List<LlmStreamChunk> collected = sub.result.get(5, TimeUnit.SECONDS);

        assertThat(collected).hasSizeGreaterThanOrEqualTo(2);
        assertThat(collected.get(collected.size() - 1).isLast()).isTrue();
        StringBuilder sb = new StringBuilder();
        collected.forEach(c -> sb.append(c.delta()));
        assertThat(sb.toString()).isEqualTo("hello world foo");
    }

    @Test
    void llmStreamChunkFactories() {
        LlmStreamChunk content = LlmStreamChunk.content("hello ");
        assertThat(content.delta()).isEqualTo("hello ");
        assertThat(content.isLast()).isFalse();

        LlmStreamChunk last = LlmStreamChunk.done(LlmResponse.FinishReason.STOP);
        assertThat(last.delta()).isEmpty();
        assertThat(last.isLast()).isTrue();

        LlmStreamChunk lastWithDelta = LlmStreamChunk.done("world", LlmResponse.FinishReason.LENGTH);
        assertThat(lastWithDelta.delta()).isEqualTo("world");
        assertThat(lastWithDelta.finishReason()).isEqualTo(LlmResponse.FinishReason.LENGTH);
    }
}
