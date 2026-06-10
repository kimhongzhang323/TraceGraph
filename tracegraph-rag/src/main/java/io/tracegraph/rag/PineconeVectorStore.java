package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.core.spi.VectorStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link VectorStore} adapter for Pinecone's REST API (v1).
 *
 * <p>{@code scope} maps to a Pinecone namespace. {@code indexHost} is the full host URL of the
 * index (e.g. {@code https://my-index-abc123.svc.us-east1-gcp.pinecone.io}).
 */
public final class PineconeVectorStore implements VectorStore {

    private final URI indexHost;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private PineconeVectorStore(Builder builder) {
        this.indexHost = Objects.requireNonNull(builder.indexHost, "indexHost");
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
        this.requestTimeout = builder.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
        ObjectNode body = mapper.createObjectNode();
        body.put("namespace", scope);
        ArrayNode vectors = body.putArray("vectors");
        ObjectNode vec = vectors.addObject();
        vec.put("id", id);
        ArrayNode values = vec.putArray("values");
        for (float v : embedding) {
            values.add(v);
        }
        if (metadata != null && !metadata.isEmpty()) {
            ObjectNode meta = vec.putObject("metadata");
            metadata.forEach(meta::put);
        }
        send("POST", "/vectors/upsert", serialize(body));
    }

    @Override
    public List<VectorMatch> query(String scope, float[] embedding, int topK) {
        ObjectNode body = mapper.createObjectNode();
        body.put("namespace", scope);
        body.put("topK", topK);
        body.put("includeMetadata", true);
        ArrayNode vector = body.putArray("vector");
        for (float v : embedding) {
            vector.add(v);
        }
        String respBody = send("POST", "/query", serialize(body));
        try {
            JsonNode root = mapper.readTree(respBody);
            JsonNode matches = root.path("matches");
            List<VectorMatch> results = new ArrayList<>();
            for (JsonNode item : matches) {
                JsonNode idNode = item.path("id");
                if (idNode.isMissingNode()) {
                    throw new VectorStoreHttpException("Pinecone match missing 'id': " + item);
                }
                String matchId = idNode.asText();
                float score = (float) item.path("score").asDouble();
                Map<String, String> meta = new HashMap<>();
                JsonNode metaNode = item.path("metadata");
                metaNode.properties().forEach(e -> meta.put(e.getKey(), e.getValue().asText()));
                results.add(new VectorMatch(matchId, score, Map.copyOf(meta)));
            }
            return List.copyOf(results);
        } catch (VectorStoreHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new VectorStoreHttpException("Failed to parse Pinecone query response", e);
        }
    }

    @Override
    public void delete(String scope, String id) {
        ObjectNode body = mapper.createObjectNode();
        body.put("namespace", scope);
        ArrayNode ids = body.putArray("ids");
        ids.add(id);
        send("POST", "/vectors/delete", serialize(body));
    }

    private String send(String method, String path, String requestBody) {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(indexHost.toString() + path))
                .header("Content-Type", "application/json")
                .header("Api-Key", apiKey);
        if (requestTimeout != null) {
            req.timeout(requestTimeout);
        }
        req.method(method, HttpRequest.BodyPublishers.ofString(requestBody));
        return JsonHttp.send(httpClient, req.build(), JsonHttp.VECTOR_STORE);
    }

    private String serialize(Object value) {
        return JsonHttp.serialize(mapper, value, JsonHttp.VECTOR_STORE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI indexHost;
        private String apiKey;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder indexHost(URI indexHost) {
            this.indexHost = Objects.requireNonNull(indexHost, "indexHost");
            return this;
        }

        public Builder indexHost(String indexHost) {
            return indexHost(URI.create(indexHost));
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

        public PineconeVectorStore build() {
            return new PineconeVectorStore(this);
        }
    }
}
