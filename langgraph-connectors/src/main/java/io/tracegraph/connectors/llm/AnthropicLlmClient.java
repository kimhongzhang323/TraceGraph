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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AnthropicLlmClient implements LlmClient {

    private static final String DEFAULT_VERSION = "2023-06-01";

    private final URI endpoint;
    private final String apiKey;
    private final String anthropicVersion;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;

    private AnthropicLlmClient(Builder b) {
        this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
        this.apiKey = b.apiKey;
        this.anthropicVersion = b.anthropicVersion;
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.requestTimeout = b.requestTimeout;
        this.mapper = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
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
            throw new UncheckedIOException("Failed to serialize Anthropic request", e);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("anthropic-version", anthropicVersion)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (apiKey != null) rb.header("x-api-key", apiKey);
        if (requestTimeout != null) rb.timeout(requestTimeout);

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Anthropic request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Anthropic request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmHttpException(response.statusCode(), new String(response.body()));
        }

        try {
            return parseResponse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Anthropic response", e);
        }
    }

    private static Map<String, Object> toRequestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("temperature", request.temperature());

        StringBuilder system = new StringBuilder();
        List<Map<String, Object>> messages = new ArrayList<>(request.messages().size());
        for (ChatMessage m : request.messages()) {
            if (m.role() == ChatMessage.Role.SYSTEM) {
                if (system.length() > 0) system.append("\n\n");
                system.append(m.content());
            } else {
                messages.add(Map.of("role", m.role().name().toLowerCase(Locale.ROOT), "content", m.content()));
            }
        }
        if (system.length() > 0) body.put("system", system.toString());
        body.put("messages", messages);
        return body;
    }

    private static LlmResponse parseResponse(JsonNode root) {
        JsonNode content = root.path("content");
        StringBuilder text = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText(""));
                }
            }
        }
        String stopReason = root.path("stop_reason").asText("");
        LlmResponse.FinishReason finish = switch (stopReason) {
            case "end_turn", "stop_sequence" -> LlmResponse.FinishReason.STOP;
            case "max_tokens" -> LlmResponse.FinishReason.LENGTH;
            default -> LlmResponse.FinishReason.OTHER;
        };
        JsonNode usage = root.path("usage");
        int prompt = usage.path("input_tokens").asInt(0);
        int completion = usage.path("output_tokens").asInt(0);
        return new LlmResponse(text.toString(), finish, new LlmResponse.Usage(prompt, completion));
    }

    public static final class Builder {
        private URI endpoint = URI.create("https://api.anthropic.com/v1/messages");
        private String apiKey;
        private String anthropicVersion = DEFAULT_VERSION;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder endpoint(URI endpoint) { this.endpoint = endpoint; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder anthropicVersion(String v) { this.anthropicVersion = v; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public AnthropicLlmClient build() { return new AnthropicLlmClient(this); }
    }
}
