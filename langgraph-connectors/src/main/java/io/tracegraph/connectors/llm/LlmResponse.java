package io.tracegraph.connectors.llm;

import java.util.Objects;

public record LlmResponse(String content, FinishReason finish, Usage usage) {

    public LlmResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(finish, "finish");
        Objects.requireNonNull(usage, "usage");
    }

    public enum FinishReason { STOP, LENGTH, OTHER }

    public record Usage(int promptTokens, int completionTokens) {
        public Usage {
            if (promptTokens < 0) throw new IllegalArgumentException("promptTokens < 0");
            if (completionTokens < 0) throw new IllegalArgumentException("completionTokens < 0");
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
