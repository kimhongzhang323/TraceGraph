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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP adapter for Anthropic Messages API endpoints.
 */
public final class AnthropicLlmClient implements LlmClient {

    private static final String DEFAULT_VERSION = "2023-06-01";
    private static final ObjectMapper ARG_MAPPER = new ObjectMapper();

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
    public String systemName() {
        return "anthropic";
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
            throw new LlmHttpException(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
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
        List<ChatMessage> all = request.messages();
        for (int i = 0; i < all.size(); i++) {
            ChatMessage m = all.get(i);
            if (m.role() == ChatMessage.Role.SYSTEM) {
                if (!system.isEmpty()) system.append("\n\n");
                system.append(m.content());
            } else if (m.role() == ChatMessage.Role.ASSISTANT && !m.toolCalls().isEmpty()) {
                // Assistant message with tool use blocks
                List<Map<String, Object>> contentBlocks = new ArrayList<>();
                if (!m.content().isEmpty()) {
                    contentBlocks.add(Map.of("type", "text", "text", m.content()));
                }
                for (ToolCall tc : m.toolCalls()) {
                    Map<String, Object> toolUse = new LinkedHashMap<>();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", tc.id());
                    toolUse.put("name", tc.name());
                    try {
                        toolUse.put("input", ARG_MAPPER.readValue(tc.arguments(), Map.class));
                    } catch (IOException e) {
                        // Preserve the model's malformed arguments instead of silently dropping them —
                        // the model must see its own output to self-correct, and traces stay faithful.
                        toolUse.put("input", Map.of("_malformed_arguments", tc.arguments()));
                    }
                    contentBlocks.add(toolUse);
                }
                messages.add(Map.of("role", "assistant", "content", contentBlocks));
            } else if (m.role() == ChatMessage.Role.TOOL) {
                // All tool results for a turn must land in a single user message.
                List<Map<String, Object>> toolResults = new ArrayList<>();
                while (true) {
                    Map<String, Object> toolResult = new LinkedHashMap<>();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", m.toolCallId());
                    toolResult.put("content", m.content());
                    toolResults.add(toolResult);
                    if (i + 1 < all.size() && all.get(i + 1).role() == ChatMessage.Role.TOOL) {
                        m = all.get(++i);
                    } else {
                        break;
                    }
                }
                messages.add(Map.of("role", "user", "content", toolResults));
            } else if (!m.contentBlocks().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>(m.contentBlocks().size() + 1);
                if (!m.content().isEmpty()) {
                    parts.add(Map.of("type", "text", "text", m.content()));
                }
                for (ContentBlock cb : m.contentBlocks()) {
                    if (cb instanceof ContentBlock.TextBlock tb) {
                        parts.add(Map.of("type", "text", "text", tb.text()));
                    } else if (cb instanceof ContentBlock.ImageBlock ib) {
                        Map<String, Object> source = new LinkedHashMap<>();
                        source.put("type", "base64");
                        source.put("media_type", ib.mimeType());
                        source.put("data", ib.base64Data());
                        Map<String, Object> imgBlock = new LinkedHashMap<>();
                        imgBlock.put("type", "image");
                        imgBlock.put("source", source);
                        parts.add(imgBlock);
                    }
                }
                messages.add(Map.of("role", m.role().name().toLowerCase(Locale.ROOT), "content", parts));
            } else {
                messages.add(Map.of("role", m.role().name().toLowerCase(Locale.ROOT), "content", m.content()));
            }
        }
        if (!system.isEmpty()) body.put("system", system.toString());
        body.put("messages", messages);

        // Include tool definitions
        if (request.hasTools()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolDefinition td : request.tools()) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", td.name());
                tool.put("description", td.description());
                if (!td.parametersSchema().isEmpty()) {
                    tool.put("input_schema", td.parametersSchema());
                } else {
                    tool.put("input_schema", Map.of("type", "object", "properties", Map.of()));
                }
                tools.add(tool);
            }
            body.put("tools", tools);
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private static LlmResponse parseResponse(JsonNode root) {
        JsonNode content = root.path("content");
        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        if (content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    text.append(block.path("text").asText(""));
                } else if ("tool_use".equals(type)) {
                    String id = block.path("id").asText("");
                    String name = block.path("name").asText("");
                    String args;
                    JsonNode inputNode = block.path("input");
                    if (inputNode.isObject()) {
                        args = inputNode.toString();
                    } else {
                        args = "{}";
                    }
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }
        }

        String stopReason = root.path("stop_reason").asText("");
        LlmResponse.FinishReason finish = switch (stopReason) {
            case "end_turn", "stop_sequence" -> LlmResponse.FinishReason.STOP;
            case "max_tokens" -> LlmResponse.FinishReason.LENGTH;
            case "tool_use" -> LlmResponse.FinishReason.TOOL_CALLS;
            default -> LlmResponse.FinishReason.OTHER;
        };
        JsonNode usage = root.path("usage");
        int prompt = usage.path("input_tokens").asInt(0);
        int completion = usage.path("output_tokens").asInt(0);
        return new LlmResponse(text.toString(), finish, new LlmResponse.Usage(prompt, completion), toolCalls);
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
