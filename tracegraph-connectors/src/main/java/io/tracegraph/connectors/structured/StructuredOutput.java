package io.tracegraph.connectors.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StructuredOutput<T> {

    private final Class<T> type;
    private final ObjectMapper mapper;

    private StructuredOutput(Class<T> type, ObjectMapper mapper) {
        this.type = Objects.requireNonNull(type, "type");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static <T> StructuredOutput<T> of(Class<T> type) {
        return new StructuredOutput<>(type, new ObjectMapper());
    }

    public static <T> StructuredOutput<T> of(Class<T> type, ObjectMapper mapper) {
        return new StructuredOutput<>(type, mapper);
    }

    public T extract(LlmResponse response) {
        Objects.requireNonNull(response, "response");
        String content = response.content().strip();
        // Strip markdown code fences if present
        if (content.startsWith("```")) {
            int newline = content.indexOf('\n');
            if (newline != -1) {
                content = content.substring(newline + 1);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3).strip();
            }
        }
        try {
            return mapper.readValue(content, type);
        } catch (Exception e) {
            throw new StructuredOutputException(
                    "Failed to parse LLM response as " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public T extractWithRetry(LlmClient client, LlmRequest request, int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        List<ChatMessage> messages = new ArrayList<>(request.messages());
        StructuredOutputException lastError = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            LlmRequest currentRequest = LlmRequest.builder()
                    .model(request.model())
                    .messages(List.copyOf(messages))
                    .temperature(request.temperature())
                    .maxTokens(request.maxTokens())
                    .tools(request.tools())
                    .build();
            LlmResponse response = client.complete(currentRequest);
            try {
                return extract(response);
            } catch (StructuredOutputException e) {
                lastError = e;
                messages.add(ChatMessage.assistant(response.content()));
                messages.add(ChatMessage.user(
                        "Your previous response could not be parsed: " + e.getMessage()
                        + ". Please respond with valid JSON matching the expected schema."));
            }
        }
        throw lastError;
    }

    public String jsonSchema() {
        // Return a minimal placeholder — full schema generation requires jackson-module-jsonSchema
        // which is not a declared dependency. Users can override by supplying a custom ObjectMapper.
        return "{\"type\":\"object\"}";
    }
}
