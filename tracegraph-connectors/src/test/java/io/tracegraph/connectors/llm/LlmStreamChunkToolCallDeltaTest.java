package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmStreamChunkToolCallDeltaTest {

    @Test
    void toolCallDeltaFactoryCreatesChunkWithCorrectFields() {
        LlmStreamChunk chunk = LlmStreamChunk.toolCallDelta(0, "get_weather", "{\"city\":");
        assertThat(chunk.isToolCallDelta()).isTrue();
        assertThat(chunk.isLast()).isFalse();
        assertThat(chunk.delta()).isEmpty();
        assertThat(chunk.toolCallDelta()).isNotNull();
        assertThat(chunk.toolCallDelta().index()).isEqualTo(0);
        assertThat(chunk.toolCallDelta().nameDelta()).isEqualTo("get_weather");
        assertThat(chunk.toolCallDelta().argsDelta()).isEqualTo("{\"city\":");
    }

    @Test
    void contentChunkHasNoToolCallDelta() {
        LlmStreamChunk chunk = LlmStreamChunk.content("hello");
        assertThat(chunk.isToolCallDelta()).isFalse();
        assertThat(chunk.toolCallDelta()).isNull();
        assertThat(chunk.isLast()).isFalse();
    }

    @Test
    void doneChunkHasNoToolCallDelta() {
        LlmStreamChunk chunk = LlmStreamChunk.done(LlmResponse.FinishReason.STOP);
        assertThat(chunk.isToolCallDelta()).isFalse();
        assertThat(chunk.toolCallDelta()).isNull();
        assertThat(chunk.isLast()).isTrue();
    }

    @Test
    void toolCallDeltaWithEmptyNameDeltaForArgumentsOnly() {
        LlmStreamChunk chunk = LlmStreamChunk.toolCallDelta(1, "", "\"NYC\"}");
        assertThat(chunk.toolCallDelta().nameDelta()).isEmpty();
        assertThat(chunk.toolCallDelta().argsDelta()).isEqualTo("\"NYC\"}");
        assertThat(chunk.toolCallDelta().index()).isEqualTo(1);
    }

    @Test
    void toolCallDeltaWithSecondIndex() {
        LlmStreamChunk chunk = LlmStreamChunk.toolCallDelta(2, "search", "");
        assertThat(chunk.toolCallDelta().index()).isEqualTo(2);
        assertThat(chunk.toolCallDelta().nameDelta()).isEqualTo("search");
        assertThat(chunk.toolCallDelta().argsDelta()).isEmpty();
    }
}
