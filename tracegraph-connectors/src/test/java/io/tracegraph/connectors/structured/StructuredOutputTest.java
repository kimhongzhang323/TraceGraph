package io.tracegraph.connectors.structured;

import io.tracegraph.connectors.llm.LlmResponse;
import org.junit.jupiter.api.Test;

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
}
