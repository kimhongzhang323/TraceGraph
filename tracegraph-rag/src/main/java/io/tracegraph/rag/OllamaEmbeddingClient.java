package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.core.spi.EmbeddingClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link EmbeddingClient} that calls the Ollama embed endpoint ({@code POST /api/embed}).
 *
 * <p>Constructed via {@link #builder()}. Thread-safe after construction.
 */
public final class OllamaEmbeddingClient implements EmbeddingClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "nomic-embed-text";

    private final URI endpoint;
    private final String model;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private OllamaEmbeddingClient(Builder builder) {
        this.endpoint = URI.create(builder.baseUrl + "/api/embed");
        this.model = builder.model;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newHttpClient();
        this.requestTimeout = builder.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embedSingle(text));
        }
        return results;
    }

    private float[] embedSingle(String text) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("input", text);

        String requestBody;
        try {
            requestBody = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new EmbeddingHttpException("Failed to serialize request", e);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (requestTimeout != null) {
            reqBuilder.timeout(requestTimeout);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingHttpException("Request interrupted", e);
        } catch (Exception e) {
            throw new EmbeddingHttpException("HTTP request failed", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new EmbeddingHttpException(response.statusCode(), response.body());
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode embeddingsNode = root.get("embeddings");
            JsonNode firstEmbedding = embeddingsNode.get(0);
            float[] emb = new float[firstEmbedding.size()];
            for (int i = 0; i < firstEmbedding.size(); i++) {
                emb[i] = (float) firstEmbedding.get(i).asDouble();
            }
            return emb;
        } catch (Exception e) {
            throw new EmbeddingHttpException("Failed to parse response", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String baseUrl = DEFAULT_BASE_URL;
        private String model = DEFAULT_MODEL;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
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

        public OllamaEmbeddingClient build() {
            return new OllamaEmbeddingClient(this);
        }
    }
}
