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

@SuppressWarnings("removal")
class OpenAiEmbeddingClientTest {

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        server.createContext("/v1/embeddings", ex -> {
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
    void parsesEmbeddingResponse() {
        respond(200, """
                {"object":"list","data":[
                  {"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}
                ],"model":"text-embedding-ada-002"}""");

        OpenAiEmbeddingClient client = OpenAiEmbeddingClient.builder()
                .endpoint(endpoint).apiKey("test-key").build();
        List<float[]> result = client.embed(List.of("hello"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void preservesOrderFromIndexField() {
        respond(200, """
                {"object":"list","data":[
                  {"object":"embedding","index":1,"embedding":[0.9,0.8]},
                  {"object":"embedding","index":0,"embedding":[0.1,0.2]}
                ],"model":"text-embedding-ada-002"}""");

        OpenAiEmbeddingClient client = OpenAiEmbeddingClient.builder()
                .endpoint(endpoint).build();
        List<float[]> result = client.embed(List.of("first", "second"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)[0]).isEqualTo(0.1f);
        assertThat(result.get(1)[0]).isEqualTo(0.9f);
    }

    @Test
    void throwsOnNon2xxResponse() {
        respond(401, """
                {"error":{"message":"Unauthorized","type":"auth_error"}}""");

        OpenAiEmbeddingClient client = OpenAiEmbeddingClient.builder()
                .endpoint(endpoint).build();

        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(LlmHttpException.class)
                .satisfies(e -> assertThat(((LlmHttpException) e).statusCode()).isEqualTo(401));
    }

    @Test
    void sendsAuthorizationHeader() {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        server.createContext("/v1/embeddings", ex -> {
            capturedAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            ex.getRequestBody().readAllBytes();
            String body = """
                    {"object":"list","data":[
                      {"object":"embedding","index":0,"embedding":[1.0]}
                    ]}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiEmbeddingClient client = OpenAiEmbeddingClient.builder()
                .endpoint(endpoint).apiKey("sk-secret").build();
        client.embed(List.of("hello"));

        assertThat(capturedAuth.get()).isEqualTo("Bearer sk-secret");
    }

    @Test
    void sendsModelInRequestBody() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/embeddings", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = """
                    {"object":"list","data":[
                      {"object":"embedding","index":0,"embedding":[0.0]}
                    ]}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        OpenAiEmbeddingClient client = OpenAiEmbeddingClient.builder()
                .endpoint(endpoint).model("text-embedding-3-small").build();
        client.embed(List.of("test"));

        assertThat(capturedBody.get()).contains("\"model\":\"text-embedding-3-small\"");
    }
}
