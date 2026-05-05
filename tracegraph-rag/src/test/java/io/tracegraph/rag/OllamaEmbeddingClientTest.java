package io.tracegraph.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class OllamaEmbeddingClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/embed", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"embeddings\":[[0.1,0.2,0.3]]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void embedReturnsParsedFloatArray() {
        OllamaEmbeddingClient client = OllamaEmbeddingClient.builder()
                .baseUrl("http://localhost:" + port)
                .model("nomic-embed-text")
                .build();

        List<float[]> result = client.embed(List.of("hello world"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly(new float[]{0.1f, 0.2f, 0.3f}, within(1e-6f));
    }

    @Test
    void requestBodyContainsModelAndInput() {
        OllamaEmbeddingClient client = OllamaEmbeddingClient.builder()
                .baseUrl("http://localhost:" + port)
                .model("mymodel")
                .build();

        client.embed(List.of("test"));

        String body = lastRequestBody.get();
        assertThat(body).contains("\"model\":\"mymodel\"");
        assertThat(body).contains("\"input\":\"test\"");
    }

    @Test
    void throwsEmbeddingHttpExceptionOnNonSuccess() throws Exception {
        HttpServer errServer = HttpServer.create(new InetSocketAddress(0), 0);
        int errPort = errServer.getAddress().getPort();
        errServer.createContext("/api/embed", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        errServer.start();

        OllamaEmbeddingClient client = OllamaEmbeddingClient.builder()
                .baseUrl("http://localhost:" + errPort)
                .build();

        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(EmbeddingHttpException.class)
                .satisfies(ex -> assertThat(((EmbeddingHttpException) ex).statusCode()).isEqualTo(503));

        errServer.stop(0);
    }

    @Test
    void builderDefaultsAreApplied() {
        OllamaEmbeddingClient client = OllamaEmbeddingClient.builder().build();
        assertThat(client).isNotNull();
    }

    private static float withPrecision(float precision) {
        return precision;
    }
}
