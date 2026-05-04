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

class OllamaLlmClientTest {

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
    void completesWithMockedResponse() {
        respond(200, """
                {"choices":[{"message":{"content":"Hello from Ollama"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":4}}""");

        OllamaLlmClient client = OllamaLlmClient.builder().endpoint(endpoint).build();
        LlmResponse r = client.complete(LlmRequest.builder()
                .model("llama3.2")
                .messages(List.of(ChatMessage.user("hi")))
                .build());

        assertThat(r.content()).isEqualTo("Hello from Ollama");
        assertThat(r.finish()).isEqualTo(LlmResponse.FinishReason.STOP);
        assertThat(r.usage().promptTokens()).isEqualTo(5);
        assertThat(r.usage().completionTokens()).isEqualTo(4);
    }
}
