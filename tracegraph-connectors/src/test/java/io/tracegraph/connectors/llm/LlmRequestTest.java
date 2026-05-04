package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRequestTest {

    @Test
    void builderProducesValidRequest() {
        LlmRequest req = LlmRequest.builder()
                .model("gpt-test")
                .messages(List.of(ChatMessage.user("hello")))
                .temperature(0.3)
                .maxTokens(64)
                .build();

        assertThat(req.model()).isEqualTo("gpt-test");
        assertThat(req.messages()).hasSize(1);
        assertThat(req.temperature()).isEqualTo(0.3);
        assertThat(req.maxTokens()).isEqualTo(64);
    }

    @Test
    void rejectsEmptyMessages() {
        assertThatThrownBy(() -> LlmRequest.builder().model("m").messages(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeTemperature() {
        assertThatThrownBy(() -> LlmRequest.builder().model("m")
                .messages(List.of(ChatMessage.user("x"))).temperature(-0.1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LlmRequest.builder().model("m")
                .messages(List.of(ChatMessage.user("x"))).temperature(2.5).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxTokens() {
        assertThatThrownBy(() -> LlmRequest.builder().model("m")
                .messages(List.of(ChatMessage.user("x"))).maxTokens(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messagesListIsImmutable() {
        java.util.ArrayList<ChatMessage> mutable = new java.util.ArrayList<>();
        mutable.add(ChatMessage.user("a"));
        LlmRequest req = LlmRequest.builder().model("m").messages(mutable).build();
        mutable.clear();
        assertThat(req.messages()).hasSize(1);
    }
}
