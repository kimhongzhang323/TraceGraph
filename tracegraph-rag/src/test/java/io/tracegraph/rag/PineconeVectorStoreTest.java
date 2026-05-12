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

class PineconeVectorStoreTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
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

    private PineconeVectorStore store() {
        return PineconeVectorStore.builder()
                .indexHost("http://localhost:" + port)
                .apiKey("test-key")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void upsertSendsVectorWithNamespace() {
        store().upsert("my-ns", "doc1", new float[]{0.1f, 0.2f}, Map.of("src", "test"));
        assertThat(lastPath.get()).isEqualTo("/vectors/upsert");
        assertThat(lastBody.get())
                .contains("\"namespace\":\"my-ns\"")
                .contains("\"id\":\"doc1\"")
                .contains("0.1")
                .contains("\"src\":\"test\"");
    }

    @Test
    void queryReturnsParsedMatches() {
        responseBody.set("{\"matches\":[{\"id\":\"doc1\",\"score\":0.95,"
                + "\"metadata\":{\"src\":\"test\"}}]}");
        List<VectorStore.VectorMatch> matches = store().query("ns", new float[]{0.1f}, 5);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).id()).isEqualTo("doc1");
        assertThat(matches.get(0).score()).isEqualTo(0.95f);
        assertThat(matches.get(0).metadata()).containsEntry("src", "test");
    }

    @Test
    void queryIncludesNamespaceAndTopK() {
        responseBody.set("{\"matches\":[]}");
        store().query("my-ns", new float[]{0.1f}, 3);
        assertThat(lastBody.get())
                .contains("\"namespace\":\"my-ns\"")
                .contains("\"topK\":3")
                .contains("\"includeMetadata\":true");
    }

    @Test
    void deleteSendsIdsInBody() {
        store().delete("ns", "doc1");
        assertThat(lastPath.get()).isEqualTo("/vectors/delete");
        assertThat(lastBody.get()).contains("\"ids\"").contains("doc1");
    }

    @Test
    void propagatesHttpError() {
        responseStatus.set(401);
        responseBody.set("{\"message\":\"Unauthorized\"}");
        assertThatThrownBy(() -> store().upsert("ns", "id", new float[]{0.1f}, null))
                .isInstanceOf(VectorStoreHttpException.class)
                .satisfies(e -> assertThat(((VectorStoreHttpException) e).statusCode()).isEqualTo(401));
    }
}
