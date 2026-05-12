package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.core.spi.VectorStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link VectorStore} adapter for Qdrant's REST API.
 *
 * <p>{@code scope} maps to a Qdrant collection name. String IDs are stored as payload
 * {@code _tg_id} because Qdrant point IDs must be UUIDs or unsigned integers; a deterministic
 * UUID is derived from the string ID via {@link UUID#nameUUIDFromBytes}.
 */
public final class QdrantVectorStore implements VectorStore {

    private final URI baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private QdrantVectorStore(Builder builder) {
        this.baseUrl = Objects.requireNonNull(builder.baseUrl, "baseUrl");
        this.apiKey = builder.apiKey;
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
        this.requestTimeout = builder.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode points = body.putArray("points");
        ObjectNode point = points.addObject();
        point.put("id", toPointId(id));
        ArrayNode vector = point.putArray("vector");
        for (float v : embedding) {
            vector.add(v);
        }
        ObjectNode payload = point.putObject("payload");
        payload.put("_tg_id", id);
        if (metadata != null) {
            metadata.forEach(payload::put);
        }
        send("PUT", "/collections/" + scope + "/points?wait=true", serialize(body));
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode vector = body.putArray("vector");
        for (float v : embedding) {
            vector.add(v);
        }
        body.put("limit", topK);
        body.put("with_payload", true);
        String respBody = send("POST", "/collections/" + scope + "/points/search", serialize(body));
        try {
            JsonNode root = mapper.readTree(respBody);
            JsonNode result = root.get("result");
            List<VectorMatch> matches = new ArrayList<>();
            for (JsonNode item : result) {
                float score = (float) item.get("score").asDouble();
                JsonNode payload = item.path("payload");
                String originalId = payload.has("_tg_id")
                        ? payload.get("_tg_id").asText()
                        : item.get("id").asText();
                Map<String, String> meta = new HashMap<>();
                payload.properties().forEach(e -> {
                    if (!"_tg_id".equals(e.getKey())) {
                        meta.put(e.getKey(), e.getValue().asText());
                    }
                });
                matches.add(new VectorMatch(originalId, score, Map.copyOf(meta)));
            }
            return List.copyOf(matches);
        } catch (Exception e) {
            throw new VectorStoreHttpException("Failed to parse Qdrant query response", e);
        }
    }

    @Override
    public void delete(String scope, String id) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode points = body.putArray("points");
        points.add(toPointId(id));
        send("POST", "/collections/" + scope + "/points/delete?wait=true", serialize(body));
    }

    private String send(String method, String path, String requestBody) {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.toString() + path))
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            req.header("api-key", apiKey);
        }
        if (requestTimeout != null) {
            req.timeout(requestTimeout);
        }
        HttpRequest.BodyPublisher publisher = requestBody != null
                ? HttpRequest.BodyPublishers.ofString(requestBody)
                : HttpRequest.BodyPublishers.noBody();
        req.method(method, publisher);
        HttpResponse<String> response;
        try {
            response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VectorStoreHttpException("Request interrupted", e);
        } catch (Exception e) {
            throw new VectorStoreHttpException("HTTP request failed", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new VectorStoreHttpException(response.statusCode(), response.body());
        }
        return response.body();
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new VectorStoreHttpException("Failed to serialize request body", e);
        }
    }

    private static String toPointId(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes()).toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI baseUrl;
        private String apiKey;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder baseUrl(URI baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            return baseUrl(URI.create(baseUrl));
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public QdrantVectorStore build() {
            return new QdrantVectorStore(this);
        }
    }
}
