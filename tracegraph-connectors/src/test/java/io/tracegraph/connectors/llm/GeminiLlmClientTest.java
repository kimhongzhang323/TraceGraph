package io.tracegraph.connectors.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiLlmClientTest {

    private static final String MODEL = "gemini-1.5-flash";
    private static final String PATH = "/v1beta/models/" + MODEL + ":generateContent";

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiLlmClient client() {
        return GeminiLlmClient.builder()
                .apiKey("test-key")
                .model(MODEL)
                .httpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
                .baseUrl(baseUrl)
                .build();
    }

    private void respond(String responseBody, AtomicReference<String> capturedBody) {
        server.createContext(PATH, ex -> {
            byte[] reqBytes = ex.getRequestBody().readAllBytes();
            if (capturedBody != null) {
                capturedBody.set(new String(reqBytes, StandardCharsets.UTF_8));
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @Test
    void sendsToolDefinitionsAndParsesToolCall() {
        String toolModel = "gemini-3-flash-preview";
        String toolPath = "/v1beta/models/" + toolModel + ":generateContent";
        server.createContext(toolPath, ex -> {
            byte[] bytes = ("""
                    {"candidates":[{"content":{"parts":[{"functionCall":{"name":"get_weather","args":{"city":"London"}}}],
                     "role":"model"},"finishReason":"STOP"}],
                     "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5}}""")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        ToolDefinition tool = new ToolDefinition("get_weather", "Get the weather",
                Map.of("type", "object", "properties",
                        Map.of("city", Map.of("type", "string"))));

        GeminiLlmClient client = GeminiLlmClient.builder()
                .apiKey("k")
                .model(toolModel)
                .baseUrl(baseUrl)
                .build();
        LlmRequest request = LlmRequest.builder()
                .model(toolModel)
                .messages(List.of(ChatMessage.user("What is the weather in London?")))
                .tools(List.of(tool))
                .build();
        LlmResponse r = client.complete(request);

        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.TOOL_CALLS);
        assertThat(r.toolCalls()).hasSize(1);
        assertThat(r.toolCalls().get(0).name()).isEqualTo("get_weather");
        assertThat(r.toolCalls().get(0).arguments()).contains("London");
    }

    @Test
    void completesWithMockedResponse() {
        respond("""
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "Hello from Gemini"}]},
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5}
                }""", null);

        LlmResponse r = client().complete(LlmRequest.builder()
                .model(MODEL)
                .messages(List.of(ChatMessage.user("hello")))
                .build());

        assertThat(r.content()).isEqualTo("Hello from Gemini");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(r.usage().promptTokens()).isEqualTo(10);
        assertThat(r.usage().completionTokens()).isEqualTo(5);
    }

    @Test
    void systemMessagesConvertedToUserTurns() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        respond("""
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "ok"}]},
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 3, "candidatesTokenCount": 1}
                }""", capturedBody);

        client().complete(LlmRequest.builder()
                .model(MODEL)
                .messages(List.of(
                        ChatMessage.system("You are a helpful assistant"),
                        ChatMessage.user("hello")))
                .build());

        assertThat(capturedBody.get())
                .contains("[System]: You are a helpful assistant")
                .contains("\"role\":\"user\"")
                .doesNotContain("\"role\":\"system\"");
    }
}
