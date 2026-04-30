package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseToolCallTest {

    @Test
    void responseWithoutToolCalls() {
        var r = new LlmResponse("hello", LlmResponse.FinishReason.STOP, new LlmResponse.Usage(5, 3));
        assertThat(r.hasToolCalls()).isFalse();
        assertThat(r.toolCalls()).isEmpty();
    }

    @Test
    void responseWithToolCalls() {
        var calls = List.of(new ToolCall("c1", "search", "{\"q\":\"x\"}"));
        var r = new LlmResponse("", LlmResponse.FinishReason.TOOL_CALLS,
                new LlmResponse.Usage(10, 5), calls);
        assertThat(r.hasToolCalls()).isTrue();
        assertThat(r.toolCalls()).hasSize(1);
        assertThat(r.toolCalls().get(0).name()).isEqualTo("search");
    }

    @Test
    void usagePlus() {
        var a = new LlmResponse.Usage(10, 5);
        var b = new LlmResponse.Usage(20, 15);
        var sum = a.plus(b);
        assertThat(sum.promptTokens()).isEqualTo(30);
        assertThat(sum.completionTokens()).isEqualTo(20);
        assertThat(sum.totalTokens()).isEqualTo(50);
    }

    @Test
    void toolCallsFinishReason() {
        assertThat(LlmResponse.FinishReason.TOOL_CALLS.name()).isEqualTo("TOOL_CALLS");
    }
}
