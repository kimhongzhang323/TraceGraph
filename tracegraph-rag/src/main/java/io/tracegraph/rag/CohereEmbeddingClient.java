package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.EmbeddingClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link EmbeddingClient} that calls the Cohere embed endpoint ({@code POST https://api.cohere.com/v1/embed}).
 *
 * <p>Constructed via {@link #builder()}. Thread-safe after construction.
 */
public final class CohereEmbeddingClient implements EmbeddingClient {

    private static final URI ENDPOINT = URI.create("https://api.cohere.com/v1/embed");
    private static final String DEFAULT_MODEL = "embed-english-v3.0";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private CohereEmbeddingClient(Builder builder) {
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.model = builder.model;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newHttpClient();
        this.requestTimeout = builder.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        String requestBody;
        try {
            StringBuilder sb = new StringBuilder("{\"texts\":[");
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(mapper.writeValueAsString(texts.get(i)));
            }
            sb.append("],\"model\":").append(mapper.writeValueAsString(model))
              .append(",\"input_type\":\"search_document\"}");
            requestBody = sb.toString();
        } catch (Exception e) {
            throw new EmbeddingHttpException("Failed to serialize request", e);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (requestTimeout != null) {
            reqBuilder.timeout(requestTimeout);
        }

        String responseBody = JsonHttp.send(httpClient, reqBuilder.build(), JsonHttp.EMBEDDING);

        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode embeddingsNode = root.get("embeddings");
            List<float[]> results = new ArrayList<>(embeddingsNode.size());
            for (JsonNode embNode : embeddingsNode) {
                float[] emb = new float[embNode.size()];
                for (int i = 0; i < embNode.size(); i++) {
                    emb[i] = (float) embNode.get(i).asDouble();
                }
                results.add(emb);
            }
            return results;
        } catch (Exception e) {
            throw new EmbeddingHttpException("Failed to parse response", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String apiKey;
        private String model = DEFAULT_MODEL;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder apiKey(String apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
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

        public CohereEmbeddingClient build() {
            return new CohereEmbeddingClient(this);
        }
    }
}
