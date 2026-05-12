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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekLlmClientTest {

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
    }

    @AfterEach
    void stop() { server.stop(0); }

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
    void completesSuccessfully() {
        respond(200, """
                {"choices":[{"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":1}}""");

        DeepSeekLlmClient client = DeepSeekLlmClient.builder()
                .apiKey("sk-test")
                .endpoint(endpoint)
                .build();

        LlmResponse r = client.complete(LlmRequest.builder()
                .model("deepseek-chat")
                .messages(List.of(ChatMessage.user("Hi")))
                .build());

        assertThat(r.content()).isEqualTo("Hello");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(r.usage().promptTokens()).isEqualTo(5);
    }

    @Test
    void propagatesHttpError() {
        respond(401, """
                {"error":{"message":"Incorrect API key"}}""");

        DeepSeekLlmClient client = DeepSeekLlmClient.builder()
                .apiKey("bad-key")
                .endpoint(endpoint)
                .build();

        assertThatThrownBy(() ->
                client.complete(LlmRequest.builder()
                        .model("deepseek-chat")
                        .messages(List.of(ChatMessage.user("Hi")))
                        .build()))
                .isInstanceOf(LlmHttpException.class);
    }

    @Test
    void defaultEndpointIsDeepSeek() {
        DeepSeekLlmClient client = DeepSeekLlmClient.builder().apiKey("k").build();
        assertThat(client.endpoint().toString())
                .isEqualTo("https://api.deepseek.com/v1/chat/completions");
    }
}
