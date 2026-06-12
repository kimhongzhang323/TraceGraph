package io.tracegraph.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** REST client for a running TraceGraph application's {@code /tracegraph} endpoints. */
final class ApiClient {

    private final URI baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    ApiClient(String baseUrl, String apiKey, HttpClient http) {
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.apiKey = apiKey;
        this.http = http;
    }

    List<String> listTraceIds() {
        JsonNode node = getJson("/tracegraph/traces");
        List<String> ids = new ArrayList<>();
        JsonNode items = node.isArray() ? node : node.path("items");
        for (JsonNode id : items) {
            ids.add(id.isTextual() ? id.asText() : id.path("executionId").asText());
        }
        return ids;
    }

    JsonNode trace(String executionId) {
        return getJson("/tracegraph/traces/" + URLEncoder.encode(executionId, StandardCharsets.UTF_8));
    }

    /**
     * Tails {@code GET /tracegraph/stream}, invoking {@code onEvent} with each SSE data payload
     * until the server closes the stream or {@code cancelled} turns true.
     */
    void streamLive(String executionId, Consumer<JsonNode> onEvent, BooleanSupplier cancelled) {
        String path = "/tracegraph/stream" + (executionId == null ? ""
                : "?execution=" + URLEncoder.encode(executionId, StandardCharsets.UTF_8));
        HttpRequest request = request(path).GET().build();
        try {
            HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() / 100 != 2) {
                try (Stream<String> body = response.body()) {
                    throw new IOException("HTTP " + response.statusCode() + " from " + path
                            + ": " + String.join("", body.toList()));
                }
            }
            try (Stream<String> lines = response.body()) {
                Iterator<String> it = lines.iterator();
                while (!cancelled.getAsBoolean() && it.hasNext()) {
                    String line = it.next();
                    if (line.startsWith("data:")) {
                        try {
                            onEvent.accept(mapper.readTree(line.substring(5).trim()));
                        } catch (IOException ignored) {
                            // skip malformed SSE payloads; stream stays open
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Live stream failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode getJson(String path) {
        try {
            HttpResponse<byte[]> response = http.send(request(path).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + path);
            }
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Request failed: " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted: " + path, e));
        }
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("X-Api-Key", apiKey);
        }
        return builder;
    }
}
