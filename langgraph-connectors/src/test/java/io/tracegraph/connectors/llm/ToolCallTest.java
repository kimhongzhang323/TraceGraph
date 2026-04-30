package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallTest {

    @Test
    void basicCreation() {
        var tc = new ToolCall("call-1", "search", "{\"query\":\"hello\"}");
        assertThat(tc.id()).isEqualTo("call-1");
        assertThat(tc.name()).isEqualTo("search");
        assertThat(tc.arguments()).isEqualTo("{\"query\":\"hello\"}");
    }
}
