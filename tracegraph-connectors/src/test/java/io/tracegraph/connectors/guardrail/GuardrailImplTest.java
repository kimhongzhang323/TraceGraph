package io.tracegraph.connectors.guardrail;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.core.spi.GuardrailVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailImplTest {

    // ── LengthGuardrail ──────────────────────────────────────────────────────

    @Test
    void lengthGuardrailAllowsShortText() {
        var g = new LengthGuardrail(100);
        assertThat(g.check("hello").isAllowed()).isTrue();
    }

    @Test
    void lengthGuardrailBlocksLongText() {
        var g = new LengthGuardrail(5);
        GuardrailVerdict v = g.check("toolongstring");
        assertThat(v.isBlocked()).isTrue();
        assertThat(v.reason()).contains("exceeds max 5");
    }

    @Test
    void lengthGuardrailAllowsNull() {
        var g = new LengthGuardrail(5);
        assertThat(g.check(null).isAllowed()).isTrue();
    }

    // ── RegexPiiGuardrail ────────────────────────────────────────────────────

    @Test
    void piiGuardrailAllowsCleanText() {
        var g = RegexPiiGuardrail.defaults();
        assertThat(g.check("Hello, how are you?").isAllowed()).isTrue();
    }

    @Test
    void piiGuardrailBlocksEmail() {
        var g = RegexPiiGuardrail.defaults();
        GuardrailVerdict v = g.check("Contact me at user@example.com please");
        assertThat(v.isBlocked()).isTrue();
    }

    @Test
    void piiGuardrailBlocksSsn() {
        var g = RegexPiiGuardrail.defaults();
        GuardrailVerdict v = g.check("My SSN is 123-45-6789");
        assertThat(v.isBlocked()).isTrue();
    }

    // ── JsonSchemaGuardrail ──────────────────────────────────────────────────

    @Test
    void jsonSchemaGuardrailAllowsValidJsonWithRequiredFields() {
        var g = new JsonSchemaGuardrail(List.of("name", "age"));
        assertThat(g.check("{\"name\":\"Alice\",\"age\":30}").isAllowed()).isTrue();
    }

    @Test
    void jsonSchemaGuardrailBlocksInvalidJson() {
        var g = new JsonSchemaGuardrail(List.of());
        GuardrailVerdict v = g.check("not-json{");
        assertThat(v.isBlocked()).isTrue();
        assertThat(v.reason()).contains("not valid JSON");
    }

    @Test
    void jsonSchemaGuardrailBlocksValidJsonMissingRequiredField() {
        var g = new JsonSchemaGuardrail(List.of("name", "email"));
        GuardrailVerdict v = g.check("{\"name\":\"Alice\"}");
        assertThat(v.isBlocked()).isTrue();
        assertThat(v.reason()).contains("email");
    }

    // ── LlmRequestGuardrail ──────────────────────────────────────────────────

    @Test
    void llmRequestGuardrailDelegatesToTextGuardrailOnUserMessage() {
        var inner = new LengthGuardrail(5);
        var g = new LlmRequestGuardrail(inner);
        LlmRequest req = LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user("toolongmessage")))
                .build();
        assertThat(g.check(req).isBlocked()).isTrue();
    }

    @Test
    void llmRequestGuardrailAllowsWhenNoUserMessage() {
        var inner = new LengthGuardrail(5);
        var g = new LlmRequestGuardrail(inner);
        LlmRequest req = LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.system("You are helpful.")))
                .build();
        assertThat(g.check(req).isAllowed()).isTrue();
    }

    @Test
    void llmRequestGuardrailChecksLastUserMessage() {
        var inner = new LengthGuardrail(5);
        var g = new LlmRequestGuardrail(inner);
        LlmRequest req = LlmRequest.builder()
                .model("test-model")
                .messages(List.of(
                        ChatMessage.user("toolongmessage"),
                        ChatMessage.assistant("ok"),
                        ChatMessage.user("hi")))
                .build();
        // Last user message is "hi" (len 2) — should allow
        assertThat(g.check(req).isAllowed()).isTrue();
    }
}
