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

public final class OllamaLlmClient implements LlmClient {

    private static final URI DEFAULT_ENDPOINT =
            URI.create("http://localhost:11434/v1/chat/completions");
    private static final String DEFAULT_MODEL = "llama3.2";

    private final URI endpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;

    private OllamaLlmClient(Builder b) {
        this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
        this.apiKey = b.apiKey;
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
            throw new UncheckedIOException("Failed to serialize Ollama request", e);
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
            throw new UncheckedIOException("Ollama request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ollama request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmHttpException(response.statusCode(), new String(response.body()));
        }

        try {
            return parseResponse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Ollama response", e);
        }
    }

    private static List<Map<String, Object>> toMessages(LlmRequest request) {
        List<Map<String, Object>> out = new ArrayList<>(request.messages().size());
        for (ChatMessage m : request.messages()) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role().name().toLowerCase(Locale.ROOT));
            msg.put("content", m.content());

            if (m.role() == ChatMessage.Role.ASSISTANT && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("type", "function");
                    tcMap.put("function", Map.of("name", tc.name(), "arguments", tc.arguments()));
                    tcs.add(tcMap);
                }
                msg.put("tool_calls", tcs);
            }

            if (m.role() == ChatMessage.Role.TOOL && m.toolCallId() != null) {
                msg.put("tool_call_id", m.toolCallId());
            }

            out.add(msg);
        }
        return out;
    }

    private static Map<String, Object> toRequestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", toMessages(request));
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());

        if (request.hasTools()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolDefinition td : request.tools()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", td.name());
                fn.put("description", td.description());
                if (!td.parametersSchema().isEmpty()) {
                    fn.put("parameters", td.parametersSchema());
                }
                tools.add(Map.of("type", "function", "function", fn));
            }
            body.put("tools", tools);
        }

        return body;
    }

    private static LlmResponse parseResponse(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("Ollama response missing 'choices'");
        }
        JsonNode first = choices.get(0);
        JsonNode message = first.path("message");
        String content = message.path("content").asText("");
        String finishStr = first.path("finish_reason").asText("");
        LlmResponse.FinishReason finish = switch (finishStr) {
            case "stop" -> LlmResponse.FinishReason.STOP;
            case "length" -> LlmResponse.FinishReason.LENGTH;
            case "tool_calls" -> LlmResponse.FinishReason.TOOL_CALLS;
            default -> LlmResponse.FinishReason.OTHER;
        };
        JsonNode usage = root.path("usage");
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completion = usage.path("completion_tokens").asInt(0);

        List<ToolCall> toolCalls = List.of();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<ToolCall> parsed = new ArrayList<>();
            for (JsonNode tcNode : toolCallsNode) {
                String id = tcNode.path("id").asText("");
                JsonNode fn = tcNode.path("function");
                String name = fn.path("name").asText("");
                String args = fn.path("arguments").asText("{}");
                parsed.add(new ToolCall(id, name, args));
            }
            toolCalls = List.copyOf(parsed);
        }

        return new LlmResponse(content, finish, new LlmResponse.Usage(prompt, completion), toolCalls);
    }

    public static final class Builder {
        private URI endpoint = DEFAULT_ENDPOINT;
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder endpoint(URI endpoint) { this.endpoint = endpoint; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public OllamaLlmClient build() { return new OllamaLlmClient(this); }
    }
}
