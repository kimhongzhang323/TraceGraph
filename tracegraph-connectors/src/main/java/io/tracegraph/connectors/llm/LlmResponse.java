package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral result from a chat completion request.
 *
 * <p>A response may contain plain text, tool calls, or both depending on the provider and finish
 * reason. Token usage is tracked separately so callers can record costs or budgets.
 */
public record LlmResponse(String content, FinishReason finish, Usage usage, List<ToolCall> toolCalls,
                          String servedModel) {

    public LlmResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(finish, "finish");
        Objects.requireNonNull(usage, "usage");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * Create a response without the served-model identifier.
     *
     * @param content textual content returned by the model
     * @param finish provider-normalized finish reason
     * @param usage token usage for the call
     * @param toolCalls tool invocations requested by the model
     */
    public LlmResponse(String content, FinishReason finish, Usage usage, List<ToolCall> toolCalls) {
        this(content, finish, usage, toolCalls, null);
    }

    /**
     * Create a response without tool call information.
     *
     * @param content textual content returned by the model
     * @param finish provider-normalized finish reason
     * @param usage token usage for the call
     */
    public LlmResponse(String content, FinishReason finish, Usage usage) {
        this(content, finish, usage, List.of(), null);
    }

    /**
     * Return whether the provider answered with a different model than was requested —
     * e.g. safeguard classifiers rerouting a request to another model. {@code false} when
     * either side is unknown.
     *
     * @param requestedModel the model id sent in the request
     * @return {@code true} when the serving model is known and differs from the requested one
     */
    public boolean rerouted(String requestedModel) {
        return servedModel != null && requestedModel != null && !servedModel.equals(requestedModel);
    }

    /**
     * Return whether the model requested any tool invocations.
     *
     * @return {@code true} when one or more tool calls are present
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * Normalized completion outcome across providers. {@link #REFUSED} maps provider-side
     * safety terminations (OpenAI {@code content_filter}, Anthropic {@code refusal}).
     */
    public enum FinishReason { STOP, LENGTH, TOOL_CALLS, REFUSED, OTHER }

    /**
     * Token accounting for a single request/response exchange.
     */
    public record Usage(int promptTokens, int completionTokens) {
        public Usage {
            if (promptTokens < 0) throw new IllegalArgumentException("promptTokens < 0");
            if (completionTokens < 0) throw new IllegalArgumentException("completionTokens < 0");
        }

        /**
         * Return prompt and completion tokens combined.
         *
         * @return total token count
         */
        public int totalTokens() {
            return promptTokens + completionTokens;
        }

        /**
         * Add two usage records together.
         *
         * @param other usage to add
         * @return combined usage
         */
        public Usage plus(Usage other) {
            return new Usage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
        }

        /**
         * Zero-token usage constant for tests and placeholder responses.
         */
        public static final Usage ZERO = new Usage(0, 0);
    }
}
