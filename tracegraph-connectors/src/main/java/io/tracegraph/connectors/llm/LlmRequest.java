package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral chat completion request.
 *
 * <p>This type captures the stable pieces TraceGraph needs across providers: chat messages, model
 * choice, sampling temperature, max token budget, and optional tool definitions.
 */
public record LlmRequest(List<ChatMessage> messages, String model, double temperature, int maxTokens,
                          List<ToolDefinition> tools) {

    public LlmRequest {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(model, "model");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be in [0, 2], was " + temperature);
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be > 0, was " + maxTokens);
        }
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    /**
     * Create a request without tool definitions.
     *
     * @param messages chat transcript to send
     * @param model provider model identifier
     * @param temperature sampling temperature in the range {@code [0, 2]}
     * @param maxTokens maximum completion token budget
     */
    public LlmRequest(List<ChatMessage> messages, String model, double temperature, int maxTokens) {
        this(messages, model, temperature, maxTokens, List.of());
    }

    /**
     * Return whether tool calling has been enabled for this request.
     *
     * @return {@code true} when at least one tool definition is present
     */
    public boolean hasTools() {
        return !tools.isEmpty();
    }

    /**
     * Create a fluent request builder with sensible defaults.
     *
     * @return request builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link LlmRequest}.
     */
    public static final class Builder {
        private List<ChatMessage> messages = List.of();
        private String model;
        private double temperature = 0.7;
        private int maxTokens = 1024;
        private List<ToolDefinition> tools = List.of();

        private Builder() {}

        /**
         * Replace the full message list.
         *
         * @param messages ordered chat messages
         * @return this builder
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Set the provider model identifier.
         *
         * @param model model name
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Set sampling temperature.
         *
         * @param t temperature in the range {@code [0, 2]}
         * @return this builder
         */
        public Builder temperature(double t) {
            this.temperature = t;
            return this;
        }

        /**
         * Set the maximum completion token budget.
         *
         * @param n positive max token count
         * @return this builder
         */
        public Builder maxTokens(int n) {
            this.maxTokens = n;
            return this;
        }

        /**
         * Set tool definitions available to the model.
         *
         * @param tools tools exposed for function calling
         * @return this builder
         */
        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Create the immutable request instance.
         *
         * @return request
         */
        public LlmRequest build() {
            return new LlmRequest(messages, model, temperature, maxTokens, tools);
        }
    }
}
