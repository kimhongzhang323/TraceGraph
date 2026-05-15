package io.tracegraph.connectors.llm;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("all")
class LlmRecordContractTest {

    @Test
    void toolCall_equalsHashCode() {
        EqualsVerifier.forClass(ToolCall.class).suppress(Warning.NULL_FIELDS).verify();
    }

    @Test
    void toolResult_equalsHashCode() {
        EqualsVerifier.forClass(ToolResult.class).suppress(Warning.NULL_FIELDS).verify();
    }

    @Test
    void toolDefinition_equalsHashCode() {
        EqualsVerifier.forClass(ToolDefinition.class).suppress(Warning.NULL_FIELDS).verify();
    }

    @Test
    void chatMessage_equalsHashCode() {
        EqualsVerifier.forClass(ChatMessage.class).suppress(Warning.NULL_FIELDS).verify();
    }

    @Test
    void llmRequest_equalsHashCode() {
        // Manual test: LlmRequest has validation in compact constructor (maxTokens > 0)
        LlmRequest r1 = new LlmRequest(List.of(ChatMessage.user("hello")), "gpt-4o", 0.5, 256);
        LlmRequest r2 = new LlmRequest(List.of(ChatMessage.user("hello")), "gpt-4o", 0.5, 256);
        LlmRequest r3 = new LlmRequest(List.of(ChatMessage.user("hi")), "gpt-4o", 0.5, 256);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    void llmResponseUsage_equalsHashCode() {
        EqualsVerifier.forClass(LlmResponse.Usage.class).verify();
    }

    @Test
    void llmStreamChunk_equalsHashCode() {
        EqualsVerifier.forClass(LlmStreamChunk.class).suppress(Warning.NULL_FIELDS).verify();
    }

    @Test
    void contentBlock_equalsHashCode() {
        EqualsVerifier.forClass(ContentBlock.class).suppress(Warning.NULL_FIELDS).verify();
    }
}
