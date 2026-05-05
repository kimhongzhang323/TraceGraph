package io.tracegraph.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * {@link EmbeddingClient} that calls the Gemini embedContent endpoint.
 *
 * <p>Endpoint: {@code POST https://generativelanguage.googleapis.com/v1beta/models/{model}:embedContent?key={apiKey}}
 *
 * <p>Constructed via {@link #builder()}. Thread-safe after construction.
 */
public final class GeminiEmbeddingClient implements EmbeddingClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "text-embedding-004";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    private GeminiEmbeddingClient(Builder builder) {
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
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embedSingle(text));
        }
        return results;
    }

    private float[] embedSingle(String text) {
        URI uri = URI.create(BASE_URL + model + ":embedContent?key=" + apiKey);

        String requestBody;
        try {
            String json = "{\"content\":{\"parts\":[{\"text\":"
                    + mapper.writeValueAsString(text) + "}]}}";
            requestBody = json;
        } catch (Exception e) {
            throw new EmbeddingHttpException("Failed to serialize request", e);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
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
            JsonNode valuesNode = root.get("embedding").get("values");
            float[] emb = new float[valuesNode.size()];
            for (int i = 0; i < valuesNode.size(); i++) {
                emb[i] = (float) valuesNode.get(i).asDouble();
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

        public GeminiEmbeddingClient build() {
            return new GeminiEmbeddingClient(this);
        }
    }
}
