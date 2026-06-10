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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class WeaviateVectorStoreTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicInteger callCount = new AtomicInteger(0);
    // response control: first call uses firstStatus, subsequent calls use laterStatus
    private final AtomicInteger firstStatus = new AtomicInteger(200);
    private final AtomicInteger laterStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{\"id\":\"some-uuid\"}");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            int call = callCount.incrementAndGet();
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().toString());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = (call == 1) ? firstStatus.get() : laterStatus.get();
            byte[] resp = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private WeaviateVectorStore store() {
        return WeaviateVectorStore.builder()
                .baseUrl("http://localhost:" + port)
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void upsertSendsPutFirst() {
        store().upsert("MyClass", "doc1", new float[]{0.1f, 0.2f}, Map.of("key", "val"));
        assertThat(lastMethod.get()).isEqualTo("PUT");
        assertThat(lastPath.get()).contains("MyClass");
        assertThat(lastBody.get()).contains("tgId").contains("doc1");
    }

    @Test
    void upsertFallsBackToPostOn404() {
        // PUT returns 404 → should retry with POST
        firstStatus.set(404);
        laterStatus.set(200);
        store().upsert("MyClass", "doc1", new float[]{0.1f}, Map.of());
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(lastMethod.get()).isEqualTo("POST");
    }

    @Test
    void queryParsesGraphQlResponse() {
        String gqlResp = "{\"data\":{\"Get\":{\"MyClass\":["
                + "{\"tgId\":\"doc1\",\"tgMeta\":\"{\\\"k\\\":\\\"v\\\"}\","
                + "\"_additional\":{\"distance\":0.0}}"
                + "]}}}";
        responseBody.set(gqlResp);
        List<VectorStore.VectorMatch> matches = store().query("MyClass", new float[]{0.1f}, 5);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).id()).isEqualTo("doc1");
        assertThat(matches.get(0).score()).isCloseTo(1.0f, within(0.001f));
        assertThat(matches.get(0).metadata()).containsEntry("k", "v");
    }

    @Test
    void deleteSucceedsOn204() {
        firstStatus.set(204);
        responseBody.set("");
        store().delete("MyClass", "doc1");
        assertThat(lastMethod.get()).isEqualTo("DELETE");
        assertThat(lastPath.get()).contains("MyClass");
    }

    @Test
    void rejectsScopeWithGraphQlMetacharacters() {
        assertThatThrownBy(() -> store().query("MyClass) { } #", new float[]{0.1f}, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThat(callCount.get()).isZero();
    }

    @Test
    void corruptMetadataSurfacesInsteadOfSilentlyDropping() {
        responseBody.set("{\"data\":{\"Get\":{\"MyClass\":["
                + "{\"tgId\":\"doc1\",\"tgMeta\":\"not json\","
                + "\"_additional\":{\"distance\":0.0}}"
                + "]}}}");
        assertThatThrownBy(() -> store().query("MyClass", new float[]{0.1f}, 5))
                .isInstanceOf(VectorStoreHttpException.class)
                .hasMessageContaining("tgMeta");
    }

    @Test
    void propagatesHttpError() {
        firstStatus.set(503);
        responseBody.set("{\"error\":\"unavailable\"}");
        assertThatThrownBy(() -> store().query("MyClass", new float[]{0.1f}, 5))
                .isInstanceOf(VectorStoreHttpException.class)
                .satisfies(e -> assertThat(((VectorStoreHttpException) e).statusCode()).isEqualTo(503));
    }
}
