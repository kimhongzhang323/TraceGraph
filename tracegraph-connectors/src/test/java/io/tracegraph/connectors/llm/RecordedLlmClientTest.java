package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordedLlmClientTest {

    private static LlmRequest request(String text) {
        return LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user(text)))
                .build();
    }

    private static LlmResponse response(String text) {
        return new LlmResponse(text, LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
    }

    @Test
    void recordsExchangesInCallOrder() {
        RecordedLlmClient recorder = RecordedLlmClient.recording(MockLlmClient.echoing());

        recorder.complete(request("first"));
        recorder.complete(request("second"));

        List<RecordedLlmClient.Exchange> exchanges = recorder.exchanges();
        assertThat(exchanges).hasSize(2);
        assertThat(exchanges.get(0).request().messages().get(0).content()).isEqualTo("first");
        assertThat(exchanges.get(1).request().messages().get(0).content()).isEqualTo("second");
    }

    @Test
    void replaysMatchingRequestWithoutTouchingNetwork() {
        List<RecordedLlmClient.Exchange> exchanges = List.of(
                new RecordedLlmClient.Exchange(request("hello"), response("recorded answer")));

        RecordedLlmClient replay = RecordedLlmClient.replaying(exchanges);

        assertThat(replay.complete(request("hello")).content()).isEqualTo("recorded answer");
        assertThat(replay.remaining()).isZero();
    }

    @Test
    void identicalRepeatedRequestsServeResponsesInRecordedOrder() {
        List<RecordedLlmClient.Exchange> exchanges = List.of(
                new RecordedLlmClient.Exchange(request("same"), response("one")),
                new RecordedLlmClient.Exchange(request("same"), response("two")));

        RecordedLlmClient replay = RecordedLlmClient.replaying(exchanges);

        assertThat(replay.complete(request("same")).content()).isEqualTo("one");
        assertThat(replay.complete(request("same")).content()).isEqualTo("two");
    }

    @Test
    void unmatchedRequestThrowsWithoutLeakingPromptText() {
        RecordedLlmClient replay = RecordedLlmClient.replaying(List.of());

        assertThatThrownBy(() -> replay.complete(request("super secret prompt")))
                .isInstanceOf(LlmReplayMismatchException.class)
                .hasMessageContaining("test-model")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("super secret prompt"));
    }

    @Test
    void unmatchedRequestGoesToFallbackWhenConfigured() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        MockLlmClient fallback = new MockLlmClient(r -> {
            fallbackCalls.incrementAndGet();
            return response("live answer");
        });
        List<RecordedLlmClient.Exchange> exchanges = List.of(
                new RecordedLlmClient.Exchange(request("known"), response("recorded")));

        RecordedLlmClient replay = RecordedLlmClient.replaying(exchanges, fallback);

        assertThat(replay.complete(request("known")).content()).isEqualTo("recorded");
        assertThat(replay.complete(request("novel")).content()).isEqualTo("live answer");
        assertThat(fallbackCalls.get()).isEqualTo(1);
    }

    @Test
    void saveAndLoadRoundTripExchanges(@TempDir Path dir) throws Exception {
        RecordedLlmClient recorder = RecordedLlmClient.recording(MockLlmClient.constant("answer"));
        recorder.complete(request("question"));
        Path file = dir.resolve("exchanges.json");
        recorder.save(file);

        List<RecordedLlmClient.Exchange> loaded = RecordedLlmClient.load(file);

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).request().messages().get(0).content()).isEqualTo("question");
        assertThat(loaded.get(0).response().content()).isEqualTo("answer");

        RecordedLlmClient replay = RecordedLlmClient.replaying(loaded);
        assertThat(replay.complete(request("question")).content()).isEqualTo("answer");
    }

    @Test
    void saveAndLoadRoundTripContentBlocks(@TempDir Path dir) throws Exception {
        LlmRequest multimodal = LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.userWithImages("look at this", List.of(
                        ContentBlock.text("look at this"),
                        ContentBlock.image("image/png", "aWJlZQ==")))))
                .build();
        RecordedLlmClient recorder = RecordedLlmClient.recording(MockLlmClient.constant("a cat"));
        recorder.complete(multimodal);
        Path file = dir.resolve("exchanges.json");
        recorder.save(file);

        List<RecordedLlmClient.Exchange> loaded = RecordedLlmClient.load(file);

        assertThat(loaded.get(0).request().messages().get(0).contentBlocks()).containsExactly(
                ContentBlock.text("look at this"),
                ContentBlock.image("image/png", "aWJlZQ=="));
        assertThat(RecordedLlmClient.replaying(loaded).complete(multimodal).content())
                .isEqualTo("a cat");
    }

    @Test
    void modeMethodsRejectWrongMode() {
        RecordedLlmClient recorder = RecordedLlmClient.recording(MockLlmClient.echoing());
        RecordedLlmClient replay = RecordedLlmClient.replaying(List.of());

        assertThatThrownBy(replay::exchanges).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(recorder::remaining).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> replay.save(Path.of("x"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void systemNameReflectsMode() {
        assertThat(RecordedLlmClient.recording(MockLlmClient.echoing()).systemName())
                .isEqualTo(MockLlmClient.echoing().systemName());
        assertThat(RecordedLlmClient.replaying(List.of()).systemName()).isEqualTo("recorded");
    }
}
