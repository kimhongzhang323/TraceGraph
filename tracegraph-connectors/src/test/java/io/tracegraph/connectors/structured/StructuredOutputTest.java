package io.tracegraph.connectors.structured;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputTest {

    record Greeting(String message) {}

    private static LlmResponse responseWith(String content) {
        return new LlmResponse(content, LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
    }

    @Test
    void extractParsesJsonIntoType() {
        StructuredOutput<Greeting> so = StructuredOutput.of(Greeting.class);
        LlmResponse response = responseWith("{\"message\":\"hello\"}");
        Greeting result = so.extract(response);
        assertThat(result.message()).isEqualTo("hello");
    }

    @Test
    void extractThrowsOnNonJson() {
        StructuredOutput<Greeting> so = StructuredOutput.of(Greeting.class);
        LlmResponse response = responseWith("not json at all");
        assertThatThrownBy(() -> so.extract(response))
                .isInstanceOf(StructuredOutputException.class);
    }

    @Test
    void jsonSchemaReturnsNonNull() {
        StructuredOutput<Greeting> so = StructuredOutput.of(Greeting.class);
        assertThat(so.jsonSchema()).isNotNull();
    }

    @Test
    void extractWithRetrySucceedsOnSecondAttempt() {
        AtomicInteger calls = new AtomicInteger(0);
        LlmClient client = request -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return new LlmResponse("not json at all", LlmResponse.FinishReason.STOP,
                        new LlmResponse.Usage(5, 3), List.of());
            }
            String lastMsg = request.messages().get(request.messages().size() - 1).content();
            assertThat(lastMsg).contains("not be parsed");
            return new LlmResponse("{\"value\":42}", LlmResponse.FinishReason.STOP,
                    new LlmResponse.Usage(5, 5), List.of());
        };

        record Box(int value) {}
        StructuredOutput<Box> so = StructuredOutput.of(Box.class);
        LlmRequest request = LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user("give me a box")))
                .build();

        Box result = so.extractWithRetry(client, request, 3);

        assertThat(result.value()).isEqualTo(42);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void extractWithRetryThrowsAfterMaxAttempts() {
        LlmClient client = req -> new LlmResponse("bad", LlmResponse.FinishReason.STOP,
                new LlmResponse.Usage(1, 1), List.of());

        record Box(int value) {}
        StructuredOutput<Box> so = StructuredOutput.of(Box.class);
        LlmRequest request = LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user("give me a box")))
                .build();

        assertThatThrownBy(() -> so.extractWithRetry(client, request, 2))
                .isInstanceOf(StructuredOutputException.class);
    }
}
