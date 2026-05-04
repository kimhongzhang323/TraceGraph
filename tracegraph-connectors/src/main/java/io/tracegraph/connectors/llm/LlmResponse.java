package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;

public record LlmResponse(String content, FinishReason finish, Usage usage, List<ToolCall> toolCalls) {

    public LlmResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(finish, "finish");
        Objects.requireNonNull(usage, "usage");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** Backwards-compatible constructor without tool calls. */
    public LlmResponse(String content, FinishReason finish, Usage usage) {
        this(content, finish, usage, List.of());
    }

    /** Returns {@code true} if the LLM requested one or more tool invocations. */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public enum FinishReason { STOP, LENGTH, TOOL_CALLS, OTHER }

    public record Usage(int promptTokens, int completionTokens) {
        public Usage {
            if (promptTokens < 0) throw new IllegalArgumentException("promptTokens < 0");
            if (completionTokens < 0) throw new IllegalArgumentException("completionTokens < 0");
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }

        /** Add two usage records together. */
        public Usage plus(Usage other) {
            return new Usage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
        }

        public static final Usage ZERO = new Usage(0, 0);
    }
}
