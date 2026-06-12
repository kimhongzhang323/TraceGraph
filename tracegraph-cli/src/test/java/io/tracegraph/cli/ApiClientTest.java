package io.tracegraph.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApiClientTest {

    private HttpServer server;
    private ApiClient client;
    private final AtomicReference<String> seenApiKey = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                "secret", HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(String path, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, ex -> {
            seenApiKey.set(ex.getRequestHeaders().getFirst("X-Api-Key"));
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @Test
    void listsTraceIdsFromPlainArrayAndSendsApiKey() {
        respond("/tracegraph/traces", "application/json", "[\"a\",\"b\"]");

        assertThat(client.listTraceIds()).containsExactly("a", "b");
        assertThat(seenApiKey.get()).isEqualTo("secret");
    }

    @Test
    void fetchesATraceDocument() {
        respond("/tracegraph/traces/exec-1", "application/json", "{\"status\":\"COMPLETED\"}");

        assertThat(client.trace("exec-1").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void liveStreamParsesSseDataLinesAndSkipsMalformed() {
        respond("/tracegraph/stream", "text/event-stream",
                "event:ENTER\ndata: {\"type\":\"ENTER\",\"nodeName\":\"a\"}\n\n"
                        + "data: {not json}\n\n"
                        + "data: {\"type\":\"COMPLETE\"}\n\n");
        List<JsonNode> events = new ArrayList<>();

        client.streamLive(null, events::add, () -> false);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).path("type").asText()).isEqualTo("ENTER");
        assertThat(events.get(1).path("type").asText()).isEqualTo("COMPLETE");
    }
}
