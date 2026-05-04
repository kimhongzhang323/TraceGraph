package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {

    @Test
    void successResult() {
        var r = ToolResult.success("call-1", "42");
        assertThat(r.callId()).isEqualTo("call-1");
        assertThat(r.content()).isEqualTo("42");
        assertThat(r.error()).isFalse();
    }

    @Test
    void errorResult() {
        var r = ToolResult.error("call-1", "not found");
        assertThat(r.callId()).isEqualTo("call-1");
        assertThat(r.content()).isEqualTo("not found");
        assertThat(r.error()).isTrue();
    }
}
