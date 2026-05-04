package io.tracegraph.connectors.llm;

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

public final class GeminiLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "gemini-1.5-flash";
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;

    private GeminiLlmClient(Builder b) {
        this.apiKey = Objects.requireNonNull(b.apiKey, "apiKey is required for GeminiLlmClient");
        this.model = b.model;
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.requestTimeout = b.requestTimeout;
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (request.hasTools()) {
            throw new UnsupportedOperationException(
                    "Tool calling not yet supported by GeminiLlmClient");
        }

        URI endpoint = URI.create(BASE_URL + model + ":generateContent?key=" + apiKey);

        byte[] body;
        try {
            body = mapper.writeValueAsBytes(toRequestBody(request));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize Gemini request", e);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (requestTimeout != null) rb.timeout(requestTimeout);

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Gemini request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmHttpException(response.statusCode(), new String(response.body()));
        }

        try {
            return parseResponse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Gemini response", e);
        }
    }

    private static List<Map<String, Object>> toContents(LlmRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>(request.messages().size());
        for (ChatMessage m : request.messages()) {
            String role;
            String text;
            if (m.role() == ChatMessage.Role.SYSTEM) {
                role = "user";
                text = "[System]: " + m.content();
            } else if (m.role() == ChatMessage.Role.ASSISTANT) {
                role = "model";
                text = m.content();
            } else {
                role = "user";
                text = m.content();
            }
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", role);
            content.put("parts", List.of(Map.of("text", text)));
            contents.add(content);
        }
        return contents;
    }

    private static Map<String, Object> toRequestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", toContents(request));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", request.temperature());
        generationConfig.put("maxOutputTokens", request.maxTokens());
        body.put("generationConfig", generationConfig);
        return body;
    }

    private static LlmResponse parseResponse(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response missing 'candidates'");
        }
        JsonNode first = candidates.get(0);
        JsonNode contentNode = first.path("content");
        JsonNode parts = contentNode.path("parts");
        StringBuilder sb = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                sb.append(part.path("text").asText(""));
            }
        }
        String content = sb.toString();

        String finishReasonRaw = first.path("finishReason").asText("");
        String finishStr = switch (finishReasonRaw) {
            case "STOP" -> "stop";
            case "MAX_TOKENS" -> "length";
            default -> finishReasonRaw.toLowerCase(Locale.ROOT);
        };
        LlmResponse.FinishReason finish = switch (finishStr) {
            case "stop" -> LlmResponse.FinishReason.STOP;
            case "length" -> LlmResponse.FinishReason.LENGTH;
            default -> LlmResponse.FinishReason.OTHER;
        };

        JsonNode usageMeta = root.path("usageMetadata");
        int promptTokens = usageMeta.path("promptTokenCount").asInt(0);
        int completionTokens = usageMeta.path("candidatesTokenCount").asInt(0);

        return new LlmResponse(content, finish, new LlmResponse.Usage(promptTokens, completionTokens),
                List.of());
    }

    public static final class Builder {
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public GeminiLlmClient build() { return new GeminiLlmClient(this); }
    }
}
