package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.core.spi.EmbeddingClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * HTTP adapter for OpenAI-compatible embeddings endpoints ({@code POST /v1/embeddings}).
 *
 * <p>Constructed via {@link #builder()}. Thread-safe after construction.
 * Non-2xx responses surface as {@link LlmHttpException}.
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

    private OpenAiEmbeddingClient(Builder b) {
        this.endpoint = b.endpoint;
        this.apiKey = b.apiKey;
        this.model = b.model;
        this.httpClient = b.httpClient != null
                ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.requestTimeout = b.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode input = body.putArray("input");
        for (String t : texts) {
            input.add(t);
        }
        body.put("model", model);

        byte[] requestBytes;
        try {
            requestBytes = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize embedding request", e);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes));
        if (apiKey != null) rb.header("Authorization", "Bearer " + apiKey);
        if (requestTimeout != null) rb.timeout(requestTimeout);

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Embedding request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmHttpException(response.statusCode(),
                    new String(response.body(), StandardCharsets.UTF_8));
        }

        try {
            return parseResponse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse embedding response", e);
        }
    }

    private static List<float[]> parseResponse(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IllegalStateException("Embedding response missing 'data' array");
        }
        record IndexedEmbedding(int index, float[] embedding) {}
        List<IndexedEmbedding> indexed = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            int idx = item.path("index").asInt();
            JsonNode embNode = item.path("embedding");
            float[] emb = new float[embNode.size()];
            for (int i = 0; i < embNode.size(); i++) {
                emb[i] = (float) embNode.get(i).asDouble();
            }
            indexed.add(new IndexedEmbedding(idx, emb));
        }
        indexed.sort(Comparator.comparingInt(IndexedEmbedding::index));
        List<float[]> result = new ArrayList<>(indexed.size());
        for (IndexedEmbedding ie : indexed) {
            result.add(ie.embedding());
        }
        return List.copyOf(result);
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

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }

        public Builder model(String model) {
            this.model = Objects.requireNonNull(model, "model");
            return this;
        }

        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }

        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public OpenAiEmbeddingClient build() { return new OpenAiEmbeddingClient(this); }
    }
}
