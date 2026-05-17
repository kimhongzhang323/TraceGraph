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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * HTTP adapter for OpenAI-compatible chat-completions endpoints.
 * <p>
 * Supports both blocking ({@link #complete}) and streaming ({@link #stream}) modes.
 * The streaming implementation sends {@code "stream":true}, reads SSE lines, and
 * emits {@link LlmStreamChunk}s including tool-call deltas.
 */
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
        this.mapper = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String systemName() {
        return "openai";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(toRequestBody(request, false));
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
            throw new LlmHttpException(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        }

        try {
            return parseResponse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse OpenAI response", e);
        }
    }

    @Override
    public Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) {
        return subscriber -> {
            SubmissionPublisher<LlmStreamChunk> pub = new SubmissionPublisher<>(
                    ForkJoinPool.commonPool(), Flow.defaultBufferSize());
            pub.subscribe(subscriber);
            Thread.startVirtualThread(() -> {
                try {
                    byte[] bodyBytes = mapper.writeValueAsBytes(toRequestBody(request, true));
                    HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                    if (apiKey != null) rb.header("Authorization", "Bearer " + apiKey);
                    if (requestTimeout != null) rb.timeout(requestTimeout);

                    HttpResponse<Stream<String>> response = httpClient.send(
                            rb.build(), HttpResponse.BodyHandlers.ofLines());
                    int status = response.statusCode();

                    try (Stream<String> lines = response.body()) {
                        if (status / 100 != 2) {
                            String errorBody = lines.collect(Collectors.joining("\n"));
                            throw new LlmHttpException(status, errorBody);
                        }
                        LlmResponse.FinishReason finishReason = LlmResponse.FinishReason.STOP;
                        Iterator<String> it = lines.iterator();
                        while (it.hasNext()) {
                            String line = it.next();
                            if (!line.startsWith("data: ")) continue;
                            String json = line.substring(6);
                            if ("[DONE]".equals(json)) break;
                            try {
                                finishReason = parseSseChunk(pub, mapper.readTree(json), finishReason);
                            } catch (IOException ignored) {
                                // Skip malformed SSE chunks — stream remains open
                            }
                        }
                        pub.submit(LlmStreamChunk.done(finishReason));
                    }

                    Thread.sleep(10);
                    pub.close();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pub.closeExceptionally(e);
                } catch (Throwable t) {
                    pub.closeExceptionally(t);
                }
            });
        };
    }

    private static LlmResponse.FinishReason parseSseChunk(
            SubmissionPublisher<LlmStreamChunk> pub, JsonNode chunk,
            LlmResponse.FinishReason current) {
        JsonNode choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return current;
        JsonNode choice = choices.get(0);

        String finishStr = choice.path("finish_reason").asText("");
        LlmResponse.FinishReason updated = finishStr.isEmpty() || "null".equals(finishStr) ? current
                : switch (finishStr) {
                    case "stop" -> LlmResponse.FinishReason.STOP;
                    case "length" -> LlmResponse.FinishReason.LENGTH;
                    case "tool_calls" -> LlmResponse.FinishReason.TOOL_CALLS;
                    default -> LlmResponse.FinishReason.OTHER;
                };

        JsonNode delta = choice.path("delta");

        // Content delta
        if (delta.has("content") && !delta.path("content").isNull()) {
            String text = delta.path("content").asText("");
            if (!text.isEmpty()) pub.submit(LlmStreamChunk.content(text));
        }

        // Tool-call deltas
        JsonNode toolCallsNode = delta.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                int idx = tc.path("index").asInt(0);
                JsonNode fn = tc.path("function");
                String nameDelta = fn.has("name") ? fn.path("name").asText("") : "";
                String argsDelta = fn.has("arguments") ? fn.path("arguments").asText("") : "";
                if (!nameDelta.isEmpty() || !argsDelta.isEmpty()) {
                    pub.submit(LlmStreamChunk.toolCallDelta(idx, nameDelta, argsDelta));
                }
            }
        }

        return updated;
    }

    private static List<Map<String, Object>> toMessages(LlmRequest request) {
        List<Map<String, Object>> out = new ArrayList<>(request.messages().size());
        for (ChatMessage m : request.messages()) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role().name().toLowerCase(Locale.ROOT));
            if (!m.contentBlocks().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>(m.contentBlocks().size() + 1);
                if (!m.content().isEmpty()) {
                    parts.add(Map.of("type", "text", "text", m.content()));
                }
                for (ContentBlock cb : m.contentBlocks()) {
                    if (cb instanceof ContentBlock.TextBlock tb) {
                        parts.add(Map.of("type", "text", "text", tb.text()));
                    } else if (cb instanceof ContentBlock.ImageBlock ib) {
                        parts.add(Map.of("type", "image_url", "image_url",
                                Map.of("url", "data:" + ib.mimeType() + ";base64," + ib.base64Data())));
                    }
                }
                msg.put("content", parts);
            } else {
                msg.put("content", m.content());
            }

            // Assistant messages with tool calls
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

            // Tool result messages
            if (m.role() == ChatMessage.Role.TOOL && m.toolCallId() != null) {
                msg.put("tool_call_id", m.toolCallId());
            }

            out.add(msg);
        }
        return out;
    }

    private static Map<String, Object> toRequestBody(LlmRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", toMessages(request));
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        if (stream) body.put("stream", true);

        // Include tool definitions if present
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
            throw new IllegalStateException("OpenAI response missing 'choices'");
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

        // Parse tool calls if present
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
