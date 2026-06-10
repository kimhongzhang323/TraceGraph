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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link VectorStore} adapter for Weaviate's REST + GraphQL APIs.
 *
 * <p>{@code scope} maps to a Weaviate class name (must start with an uppercase letter per
 * Weaviate's naming rules). Metadata is serialized as a JSON string in a single property
 * {@code tgMeta} so the class schema requires no prior knowledge of key names.
 * The class schema must define {@code tgId} (text) and {@code tgMeta} (text) properties.
 * String IDs are mapped deterministically to UUIDs via {@link UUID#nameUUIDFromBytes}.
 */
public final class WeaviateVectorStore implements VectorStore {

    private final URI baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private WeaviateVectorStore(Builder builder) {
        this.baseUrl = Objects.requireNonNull(builder.baseUrl, "baseUrl");
        this.apiKey = builder.apiKey;
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
        this.requestTimeout = builder.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        JsonHttp.requireSafeIdentifier("scope", scope);
        String uuid = toUuid(id);
        ObjectNode props = buildProps(id, metadata);
        ArrayNode vector = buildVector(embedding);

        // Try PUT (update existing) first; fall back to POST (create new) on 404
        ObjectNode updateBody = mapper.createObjectNode();
        updateBody.put("class", scope);
        updateBody.set("properties", props);
        updateBody.set("vector", vector);
        try {
            send("PUT", "/v1/objects/" + scope + "/" + uuid, serialize(updateBody));
        } catch (VectorStoreHttpException e) {
            if (e.statusCode() == 404) {
                ObjectNode createBody = mapper.createObjectNode();
                createBody.put("class", scope);
                createBody.put("id", uuid);
                createBody.set("properties", props);
                createBody.set("vector", vector);
                send("POST", "/v1/objects", serialize(createBody));
            } else {
                throw e;
            }
        }
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        // scope is interpolated into the GraphQL document — reject anything that could escape it
        JsonHttp.requireSafeIdentifier("scope", scope);
        String vectorJson = serialize(buildVector(embedding));
        String gql = "{ Get { " + scope + "(nearVector: {vector: " + vectorJson + "} limit: " + topK + ") "
                + "{ tgId tgMeta _additional { distance } } } }";
        ObjectNode body = mapper.createObjectNode();
        body.put("query", gql);
        String respBody = send("POST", "/v1/graphql", serialize(body));
        try {
            JsonNode root = mapper.readTree(respBody);
            JsonNode items = root.path("data").path("Get").path(scope);
            List<VectorMatch> matches = new ArrayList<>();
            for (JsonNode item : items) {
                String originalId = item.path("tgId").asText();
                double distance = item.path("_additional").path("distance").asDouble(0.0);
                float score = (float) (1.0 - distance);
                Map<String, String> meta = deserializeMeta(item.path("tgMeta").asText("{}"));
                matches.add(new VectorMatch(originalId, score, meta));
            }
            return List.copyOf(matches);
        } catch (VectorStoreHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new VectorStoreHttpException("Failed to parse Weaviate query response", e);
        }
    }

    @Override
    public void delete(String scope, String id) {
        JsonHttp.requireSafeIdentifier("scope", scope);
        String uuid = toUuid(id);
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.toString() + "/v1/objects/" + scope + "/" + uuid))
                .DELETE();
        if (apiKey != null && !apiKey.isEmpty()) {
            req.header("Authorization", "Bearer " + apiKey);
        }
        if (requestTimeout != null) {
            req.timeout(requestTimeout);
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VectorStoreHttpException("Request interrupted", e);
        } catch (Exception e) {
            throw new VectorStoreHttpException("HTTP request failed", e);
        }
        // 204 = deleted, 404 = already absent — both are acceptable
        if (response.statusCode() != 204 && response.statusCode() != 200 && response.statusCode() != 404) {
            throw new VectorStoreHttpException(response.statusCode(), response.body());
        }
    }

    private ObjectNode buildProps(String id, Map<String, String> metadata) {
        ObjectNode props = mapper.createObjectNode();
        props.put("tgId", id);
        props.put("tgMeta", serializeMeta(metadata));
        return props;
    }

    private ArrayNode buildVector(float[] embedding) {
        ArrayNode arr = mapper.createArrayNode();
        for (float v : embedding) {
            arr.add(v);
        }
        return arr;
    }

    private String send(String method, String path, String requestBody) {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.toString() + path))
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            req.header("Authorization", "Bearer " + apiKey);
        }
        if (requestTimeout != null) {
            req.timeout(requestTimeout);
        }
        req.method(method, HttpRequest.BodyPublishers.ofString(requestBody));
        return JsonHttp.send(httpClient, req.build(), JsonHttp.VECTOR_STORE);
    }

    private String serialize(Object value) {
        return JsonHttp.serialize(mapper, value, JsonHttp.VECTOR_STORE);
    }

    private String serializeMeta(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return serialize(metadata);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeMeta(String json) {
        try {
            return (Map<String, String>) mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new VectorStoreHttpException("Failed to parse tgMeta payload: " + json, e);
        }
    }

    private static String toUuid(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
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

        public WeaviateVectorStore build() {
            return new WeaviateVectorStore(this);
        }
    }
}
