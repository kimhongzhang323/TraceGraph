package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class OpenAiLlmClient implements LlmClient {

    private final URI endpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;

    private OpenAiLlmClient(Builder b) {
        this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
        this.apiKey = b.apiKey;
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.requestTimeout = b.requestTimeout;
        this.mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(toRequestBody(request));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize OpenAI request", e);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (apiKey != null) rb.header("Authorization", "Bearer " + apiKey);
        if (requestTimeout != null) rb.timeout(requestTimeout);

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("OpenAI request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmHttpException(response.statusCode(), new String(response.body()));
        }

        try {
            return parseResponse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse OpenAI response", e);
        }
    }

    private static List<java.util.Map<String, Object>> toMessages(LlmRequest request) {
        List<java.util.Map<String, Object>> out = new ArrayList<>(request.messages().size());
        for (ChatMessage m : request.messages()) {
            out.add(java.util.Map.of("role", m.role().name().toLowerCase(Locale.ROOT), "content", m.content()));
        }
        return out;
    }

    private static java.util.Map<String, Object> toRequestBody(LlmRequest request) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", toMessages(request));
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        return body;
    }

    private static LlmResponse parseResponse(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI response missing 'choices'");
        }
        JsonNode first = choices.get(0);
        String content = first.path("message").path("content").asText("");
        String finishStr = first.path("finish_reason").asText("");
        LlmResponse.FinishReason finish = switch (finishStr) {
            case "stop" -> LlmResponse.FinishReason.STOP;
            case "length" -> LlmResponse.FinishReason.LENGTH;
            default -> LlmResponse.FinishReason.OTHER;
        };
        JsonNode usage = root.path("usage");
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completion = usage.path("completion_tokens").asInt(0);
        return new LlmResponse(content, finish, new LlmResponse.Usage(prompt, completion));
    }

    public static final class Builder {
        private URI endpoint = URI.create("https://api.openai.com/v1/chat/completions");
        private String apiKey;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder endpoint(URI endpoint) { this.endpoint = endpoint; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public OpenAiLlmClient build() { return new OpenAiLlmClient(this); }
    }
}
