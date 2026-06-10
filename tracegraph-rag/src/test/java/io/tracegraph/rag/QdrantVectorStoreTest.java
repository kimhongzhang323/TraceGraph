package io.tracegraph.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.tracegraph.core.spi.VectorStore;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantVectorStoreTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{\"result\":{},\"status\":\"ok\"}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().toString());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private QdrantVectorStore store() {
        return QdrantVectorStore.builder()
                .baseUrl("http://localhost:" + port)
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void upsertSendsPointsToPut() {
        store().upsert("my-collection", "doc1", new float[]{0.1f, 0.2f}, Map.of("k", "v"));
        assertThat(lastMethod.get()).isEqualTo("PUT");
        assertThat(lastPath.get()).startsWith("/collections/my-collection/points");
        assertThat(lastBody.get()).contains("_tg_id").contains("doc1").contains("0.1");
    }

    @Test
    void queryReturnsParsedMatches() {
        responseBody.set("{\"result\":[{\"id\":\"uuid1\",\"score\":0.9,"
                + "\"payload\":{\"_tg_id\":\"doc1\",\"source\":\"test\"}}],\"status\":\"ok\"}");
        List<VectorStore.VectorMatch> matches = store().query("col", new float[]{0.1f}, 5);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).id()).isEqualTo("doc1");
        assertThat(matches.get(0).score()).isEqualTo(0.9f);
        assertThat(matches.get(0).metadata()).containsEntry("source", "test");
    }

    @Test
    void deleteSendsDeleteRequest() {
        store().delete("col", "doc1");
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastPath.get()).contains("delete");
        assertThat(lastBody.get()).contains("points");
    }

    @Test
    void rejectsScopeWithPathCharacters() {
        assertThatThrownBy(() -> store().query("col/../other", new float[]{0.1f}, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThat(lastMethod.get()).isNull();
    }

    @Test
    void malformedQueryResponseThrowsWithClearMessage() {
        responseBody.set("{\"status\":\"ok\"}");
        assertThatThrownBy(() -> store().query("col", new float[]{0.1f}, 5))
                .isInstanceOf(VectorStoreHttpException.class)
                .hasMessageContaining("missing 'result'");
    }

    @Test
    void propagatesHttpError() {
        responseStatus.set(503);
        responseBody.set("{\"status\":\"error\"}");
        assertThatThrownBy(() -> store().upsert("col", "id", new float[]{0.1f}, null))
                .isInstanceOf(VectorStoreHttpException.class)
                .satisfies(e -> assertThat(((VectorStoreHttpException) e).statusCode()).isEqualTo(503));
    }
}
