package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;

public record LlmRequest(List<ChatMessage> messages, String model, double temperature, int maxTokens) {

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
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<ChatMessage> messages = List.of();
        private String model;
        private double temperature = 0.7;
        private int maxTokens = 1024;

        private Builder() {}

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(double t) {
            this.temperature = t;
            return this;
        }

        public Builder maxTokens(int n) {
            this.maxTokens = n;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(messages, model, temperature, maxTokens);
        }
    }
}
