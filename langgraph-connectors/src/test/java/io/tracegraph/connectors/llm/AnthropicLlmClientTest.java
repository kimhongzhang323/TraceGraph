package io.tracegraph.connectors.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicLlmClientTest {

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        server.createContext("/v1/messages", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @Test
    void parsesSuccessfulResponse() {
        respond(200, """
                {"content":[{"type":"text","text":"hello back"}],
                 "stop_reason":"end_turn",
                 "usage":{"input_tokens":12,"output_tokens":4}}""");

        AnthropicLlmClient client = AnthropicLlmClient.builder().endpoint(endpoint).apiKey("k").build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("claude-opus-4-7").messages(List.of(ChatMessage.user("hi"))).build());

        assertThat(r.content()).isEqualTo("hello back");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(r.usage().promptTokens()).isEqualTo(12);
        assertThat(r.usage().completionTokens()).isEqualTo(4);
    }

    @Test
    void concatenatesMultipleTextBlocks() {
        respond(200, """
                {"content":[{"type":"text","text":"part 1 "},
                            {"type":"text","text":"part 2"}],
                 "stop_reason":"end_turn",
                 "usage":{"input_tokens":1,"output_tokens":2}}""");

        AnthropicLlmClient client = AnthropicLlmClient.builder().endpoint(endpoint).build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build());

        assertThat(r.content()).isEqualTo("part 1 part 2");
    }

    @Test
    void mapsMaxTokensToLength() {
        respond(200, """
                {"content":[{"type":"text","text":"truncated"}],
                 "stop_reason":"max_tokens",
                 "usage":{"input_tokens":1,"output_tokens":1}}""");

        AnthropicLlmClient client = AnthropicLlmClient.builder().endpoint(endpoint).build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build());

        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.LENGTH);
    }

    @Test
    void mapsUnknownStopReasonToOther() {
        respond(200, """
                {"content":[{"type":"text","text":"x"}],
                 "stop_reason":"tool_use",
                 "usage":{"input_tokens":0,"output_tokens":0}}""");

        AnthropicLlmClient client = AnthropicLlmClient.builder().endpoint(endpoint).build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build());

        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.OTHER);
    }

    @Test
    void liftsSystemMessagesAndSendsRequiredHeaders() {
        AtomicReference<String> capturedKey = new AtomicReference<>();
        AtomicReference<String> capturedVersion = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/messages", ex -> {
            capturedKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
            capturedVersion.set(ex.getRequestHeaders().getFirst("anthropic-version"));
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = """
                    {"content":[{"type":"text","text":"ok"}],
                     "stop_reason":"end_turn",
                     "usage":{"input_tokens":0,"output_tokens":0}}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        AnthropicLlmClient client = AnthropicLlmClient.builder()
                .endpoint(endpoint).apiKey("sk-ant-x").anthropicVersion("2024-10-22").build();
        client.complete(LlmRequest.builder()
                .model("claude-opus-4-7")
                .messages(List.of(
                        ChatMessage.system("rule a"),
                        ChatMessage.system("rule b"),
                        ChatMessage.user("hi"),
                        ChatMessage.assistant("hello"),
                        ChatMessage.user("again")))
                .temperature(0.3).maxTokens(50).build());

        assertThat(capturedKey.get()).isEqualTo("sk-ant-x");
        assertThat(capturedVersion.get()).isEqualTo("2024-10-22");
        assertThat(capturedBody.get())
                .contains("\"system\":\"rule a\\n\\nrule b\"")
                .contains("\"role\":\"user\"")
                .contains("\"role\":\"assistant\"")
                .contains("\"max_tokens\":50")
                .contains("\"temperature\":0.3")
                .doesNotContain("\"role\":\"system\"");
    }

    @Test
    void throwsLlmHttpExceptionOnNon2xx() {
        respond(401, "{\"error\":{\"type\":\"authentication_error\"}}");

        AnthropicLlmClient client = AnthropicLlmClient.builder().endpoint(endpoint).apiKey("bad").build();
        assertThatThrownBy(() -> client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build()))
                .isInstanceOf(LlmHttpException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("authentication_error");
    }
}
