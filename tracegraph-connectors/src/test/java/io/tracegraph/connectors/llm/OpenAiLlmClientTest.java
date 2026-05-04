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

class OpenAiLlmClientTest {

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        server.createContext("/v1/chat/completions", ex -> {
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
                {"choices":[{"message":{"content":"hi there"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":7,"completion_tokens":3}}""");

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).apiKey("test-key").build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("gpt-4o").messages(List.of(ChatMessage.user("hello"))).build());

        assertThat(r.content()).isEqualTo("hi there");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(r.usage().promptTokens()).isEqualTo(7);
        assertThat(r.usage().completionTokens()).isEqualTo(3);
    }

    @Test
    void mapsLengthFinishReason() {
        respond(200, """
                {"choices":[{"message":{"content":"truncated"},"finish_reason":"length"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1}}""");

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build());

        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.LENGTH);
    }

    @Test
    void mapsUnknownFinishToOther() {
        respond(200, """
                {"choices":[{"message":{"content":"x"},"finish_reason":"content_filter"}],
                 "usage":{"prompt_tokens":0,"completion_tokens":0}}""");

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build());

        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.OTHER);
    }

    @Test
    void sendsAuthorizationHeaderAndJsonBody() {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", ex -> {
            capturedAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            capturedContentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            byte[] req = ex.getRequestBody().readAllBytes();
            capturedBody.set(new String(req, StandardCharsets.UTF_8));
            String body = """
                    {"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":0,"completion_tokens":0}}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).apiKey("sk-abc").build();
        client.complete(LlmRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatMessage.system("you are x"), ChatMessage.user("hi")))
                .temperature(0.5).maxTokens(100).build());

        assertThat(capturedAuth.get()).isEqualTo("Bearer sk-abc");
        assertThat(capturedContentType.get()).isEqualTo("application/json");
        assertThat(capturedBody.get())
                .contains("\"model\":\"gpt-4o-mini\"")
                .contains("\"role\":\"system\"")
                .contains("\"role\":\"user\"")
                .contains("\"temperature\":0.5")
                .contains("\"max_tokens\":100");
    }

    @Test
    void omitsAuthorizationWhenNoKey() {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        capturedAuth.set("__sentinel__");
        server.createContext("/v1/chat/completions", ex -> {
            capturedAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            String body = """
                    {"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":0,"completion_tokens":0}}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("hi"))).build());

        assertThat(capturedAuth.get()).isNull();
    }

    @Test
    void throwsLlmHttpExceptionOnNon2xx() {
        respond(429, "{\"error\":{\"message\":\"rate limited\"}}");

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        assertThatThrownBy(() -> client.complete(LlmRequest.builder()
                .model("m").messages(List.of(ChatMessage.user("x"))).build()))
                .isInstanceOf(LlmHttpException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("rate limited");
    }

    @Test
    void serializesToolDefinitionsAndCalls() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = """
                    {"choices":[{"message":{"content":null,"tool_calls":[{"id":"call_abc","type":"function","function":{"name":"get_weather","arguments":"{\\"location\\":\\"SF\\"}"}}]},"finish_reason":"tool_calls"}],
                     "usage":{"prompt_tokens":0,"completion_tokens":0}}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiLlmClient client = OpenAiLlmClient.builder().endpoint(endpoint).build();
        
        ToolDefinition toolDef = new ToolDefinition("get_weather", "Get the weather", java.util.Map.of("type", "object"));
        
        LlmRequest req = LlmRequest.builder()
                .model("m")
                .messages(List.of(
                        ChatMessage.user("weather?"),
                        ChatMessage.assistantWithToolCalls("", List.of(new ToolCall("call_000", "some_tool", "{\"a\":1}"))),
                        ChatMessage.toolResult("call_000", "tool_result_content")
                ))
                .tools(List.of(toolDef))
                .build();

        LlmResponse r = client.complete(req);

        // Check request body serialization
        String sentBody = capturedBody.get();
        assertThat(sentBody)
                .contains("\"name\":\"get_weather\"") // Tool definition
                .contains("\"description\":\"Get the weather\"")
                .contains("\"tool_calls\":[{") // Assistant tool call
                .contains("\"id\":\"call_000\"")
                .contains("\"name\":\"some_tool\"")
                .contains("\"arguments\":\"{\\\"a\\\":1}\"")
                .contains("\"role\":\"tool\"") // Tool result
                .contains("\"tool_call_id\":\"call_000\"")
                .contains("\"content\":\"tool_result_content\"");

        // Check response parsing
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.TOOL_CALLS);
        assertThat(r.toolCalls()).hasSize(1);
        assertThat(r.toolCalls().get(0).name()).isEqualTo("get_weather");
        assertThat(r.toolCalls().get(0).id()).isEqualTo("call_abc");
        assertThat(r.toolCalls().get(0).arguments()).contains("SF");
    }
}
