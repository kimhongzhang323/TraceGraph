package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    @Test
    void echoingReturnsLastMessageContent() {
        MockLlmClient client = MockLlmClient.echoing();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("m")
                .messages(List.of(ChatMessage.system("be nice"), ChatMessage.user("ping")))
                .build());

        assertThat(r.content()).isEqualTo("ping");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
    }

    @Test
    void constantReturnsConfiguredText() {
        MockLlmClient client = MockLlmClient.constant("42");
        LlmResponse r = client.complete(LlmRequest.builder().model("m")
                .messages(List.of(ChatMessage.user("anything"))).build());
        assertThat(r.content()).isEqualTo("42");
        assertThat(r.usage().completionTokens()).isEqualTo(2);
    }

    @Test
    void recordsCallsForAssertion() {
        MockLlmClient client = MockLlmClient.constant("ok");
        client.complete(LlmRequest.builder().model("m1").messages(List.of(ChatMessage.user("a"))).build());
        client.complete(LlmRequest.builder().model("m2").messages(List.of(ChatMessage.user("b"))).build());

        assertThat(client.calls()).hasSize(2);
        assertThat(client.calls().get(0).model()).isEqualTo("m1");
        assertThat(client.calls().get(1).model()).isEqualTo("m2");
    }
}
