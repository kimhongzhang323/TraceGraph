package io.tracegraph.connectors.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class GeminiLlmClient implements LlmClient {

    // Preview model name — update to stable once gemini-3-flash is GA
    private static final String DEFAULT_MODEL = "gemini-3-flash-preview";
    static final String DEFAULT_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final ObjectMapper ARG_MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;

    private GeminiLlmClient(Builder b) {
        this.apiKey = Objects.requireNonNull(b.apiKey, "apiKey is required for GeminiLlmClient");
        this.model = b.model;
        this.baseUrl = b.baseUrl;
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
        // API key goes in a header, not the query string — URLs leak into logs and proxies.
        URI endpoint = URI.create(baseUrl + model + ":generateContent");

        byte[] body = JsonHttp.writeBody(mapper, toRequestBody(request), "Gemini");

        HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (requestTimeout != null) rb.timeout(requestTimeout);

        return parseResponse(JsonHttp.sendForJson(httpClient, rb.build(), mapper, "Gemini"), mapper);
    }

    private static List<Map<String, Object>> toContents(LlmRequest request) {
        List<ChatMessage> all = request.messages();
        List<Map<String, Object>> contents = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            ChatMessage m = all.get(i);
            if (m.role() == ChatMessage.Role.ASSISTANT && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>();
                if (!m.content().isEmpty()) {
                    parts.add(Map.of("text", m.content()));
                }
                for (ToolCall tc : m.toolCalls()) {
                    parts.add(Map.of("functionCall",
                            Map.of("name", tc.name(), "args", parseArgs(tc.arguments()))));
                }
                contents.add(content("model", parts));
            } else if (m.role() == ChatMessage.Role.TOOL) {
                // All tool results for a turn land in one user content as functionResponse parts.
                List<Map<String, Object>> parts = new ArrayList<>();
                while (true) {
                    parts.add(Map.of("functionResponse", Map.of(
                            "name", m.toolCallId() == null ? "" : m.toolCallId(),
                            "response", Map.of("content", m.content()))));
                    if (i + 1 < all.size() && all.get(i + 1).role() == ChatMessage.Role.TOOL) {
                        m = all.get(++i);
                    } else {
                        break;
                    }
                }
                contents.add(content("user", parts));
            } else if (m.role() == ChatMessage.Role.SYSTEM) {
                contents.add(content("user", List.of(Map.of("text", "[System]: " + m.content()))));
            } else {
                String role = m.role() == ChatMessage.Role.ASSISTANT ? "model" : "user";
                contents.add(content(role, List.of(Map.of("text", m.content()))));
            }
        }
        return contents;
    }

    private static Map<String, Object> content(String role, List<Map<String, Object>> parts) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", role);
        content.put("parts", parts);
        return content;
    }

    private static Object parseArgs(String arguments) {
        try {
            return ARG_MAPPER.readValue(arguments, Map.class);
        } catch (IOException e) {
            // Preserve the model's malformed arguments instead of silently dropping them —
            // the model must see its own output to self-correct, and traces stay faithful.
            return Map.of("_malformed_arguments", arguments);
        }
    }

    private static Map<String, Object> toRequestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", toContents(request));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", request.temperature());
        generationConfig.put("maxOutputTokens", request.maxTokens());
        body.put("generationConfig", generationConfig);
        if (request.hasTools()) {
            List<Map<String, Object>> functionDeclarations = new ArrayList<>();
            for (ToolDefinition td : request.tools()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", td.name());
                fn.put("description", td.description());
                if (!td.parametersSchema().isEmpty()) {
                    fn.put("parameters", td.parametersSchema());
                }
                functionDeclarations.add(fn);
            }
            body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
        }
        return body;
    }

    private static LlmResponse parseResponse(JsonNode root, ObjectMapper mapper) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response missing 'candidates'");
        }
        JsonNode first = candidates.get(0);
        JsonNode contentNode = first.path("content");
        JsonNode parts = contentNode.path("parts");
        StringBuilder sb = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    sb.append(part.path("text").asText(""));
                } else if (part.has("functionCall")) {
                    JsonNode fc = part.path("functionCall");
                    String name = fc.path("name").asText("");
                    String args;
                    try {
                        args = mapper.writeValueAsString(fc.path("args"));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to serialize tool call args", e);
                    }
                    toolCalls.add(new ToolCall(name, name, args));
                }
            }
        }
        String content = sb.toString();

        String finishReasonRaw = first.path("finishReason").asText("");
        LlmResponse.FinishReason finish;
        if (!toolCalls.isEmpty()) {
            finish = LlmResponse.FinishReason.TOOL_CALLS;
        } else {
            String finishStr = switch (finishReasonRaw) {
                case "STOP" -> "stop";
                case "MAX_TOKENS" -> "length";
                default -> finishReasonRaw.toLowerCase(Locale.ROOT);
            };
            finish = switch (finishStr) {
                case "stop" -> LlmResponse.FinishReason.STOP;
                case "length" -> LlmResponse.FinishReason.LENGTH;
                default -> LlmResponse.FinishReason.OTHER;
            };
        }

        JsonNode usageMeta = root.path("usageMetadata");
        int promptTokens = usageMeta.path("promptTokenCount").asInt(0);
        int completionTokens = usageMeta.path("candidatesTokenCount").asInt(0);

        return new LlmResponse(content, finish, new LlmResponse.Usage(promptTokens, completionTokens),
                List.copyOf(toolCalls));
    }

    public static final class Builder {
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private String baseUrl = DEFAULT_BASE_URL;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        /** Package-private — for testing only. Override the base URL to point at a local mock server. */
        Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }

        public GeminiLlmClient build() { return new GeminiLlmClient(this); }
    }
}
