package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.core.spi.EmbeddingClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@link EmbeddingClient} that calls the OpenAI-compatible embeddings endpoint.
 *
 * <p>Constructed via {@link #builder()}. Thread-safe after construction.
 */
public final class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final URI DEFAULT_ENDPOINT =
            URI.create("https://api.openai.com/v1/embeddings");
    private static final String DEFAULT_MODEL = "text-embedding-ada-002";

    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private OpenAiEmbeddingClient(Builder builder) {
        this.endpoint = builder.endpoint;
        this.apiKey = builder.apiKey;
        this.model = builder.model;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newHttpClient();
        this.requestTimeout = builder.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode input = body.putArray("input");
        for (String t : texts) {
            input.add(t);
        }
        body.put("model", model);

        String requestBody = JsonHttp.serialize(mapper, body, JsonHttp.EMBEDDING);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (apiKey != null && !apiKey.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }
        if (requestTimeout != null) {
            reqBuilder.timeout(requestTimeout);
        }

        String responseBody = JsonHttp.send(httpClient, reqBuilder.build(), JsonHttp.EMBEDDING);

        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode data = root.get("data");
            List<IndexedEmbedding> indexed = new ArrayList<>();
            for (JsonNode item : data) {
                int index = item.get("index").asInt();
                JsonNode embNode = item.get("embedding");
                float[] emb = new float[embNode.size()];
                for (int i = 0; i < embNode.size(); i++) {
                    emb[i] = (float) embNode.get(i).asDouble();
                }
                indexed.add(new IndexedEmbedding(index, emb));
            }
            indexed.sort(Comparator.comparingInt(IndexedEmbedding::index));
            List<float[]> result = new ArrayList<>(indexed.size());
            for (IndexedEmbedding ie : indexed) {
                result.add(ie.embedding());
            }
            return result;
        } catch (Exception e) {
            throw new EmbeddingHttpException("Failed to parse response", e);
        }
    }

    private record IndexedEmbedding(int index, float[] embedding) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI endpoint = DEFAULT_ENDPOINT;
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder endpoint(URI endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = Objects.requireNonNull(model, "model");
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

        public OpenAiEmbeddingClient build() {
            return new OpenAiEmbeddingClient(this);
        }
    }
}
