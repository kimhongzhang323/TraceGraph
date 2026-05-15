package io.tracegraph.memory.window;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compresses conversation history by summarising it with an LLM when the buffer exceeds a threshold.
 *
 * <p>Equivalent to LangChain's {@code ConversationSummaryBufferMemory}. When message count reaches
 * {@code compressAt}, the oldest messages (all except the last {@code keepRecent}) are summarised
 * into a single system message, reducing token usage while preserving context.
 *
 * <pre>{@code
 * SummaryMemory mem = SummaryMemory.builder()
 *     .llmClient(client).model("gpt-4o-mini")
 *     .compressAt(20).keepRecent(5)
 *     .build();
 * mem.add(ChatMessage.user("..."));
 * List<ChatMessage> history = mem.messages(); // summary + recent
 * }</pre>
 */
public final class SummaryMemory {

    private static final String SUMMARY_PROMPT =
            "Summarise the following conversation concisely, preserving key facts and decisions. "
            + "Respond with only the summary, no preamble.\n\n";

    private final LlmClient llmClient;
    private final String model;
    private final int compressAt;
    private final int keepRecent;
    private final List<ChatMessage> messages = new ArrayList<>();

    private SummaryMemory(Builder b) {
        this.llmClient = b.llmClient;
        this.model = b.model;
        this.compressAt = b.compressAt;
        this.keepRecent = b.keepRecent;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Add a message, triggering compression if the buffer has reached {@code compressAt}.
     *
     * @param message message to add
     */
    public synchronized void add(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        messages.add(message);
        if (messages.size() >= compressAt) {
            compress();
        }
    }

    /**
     * Return all messages — a summary system message (if compressed) followed by recent messages.
     *
     * @return immutable snapshot
     */
    public synchronized List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    /** Clear all messages and any accumulated summary. */
    public synchronized void clear() {
        messages.clear();
    }

    public synchronized int size() {
        return messages.size();
    }

    private void compress() {
        int toSummarise = messages.size() - keepRecent;
        if (toSummarise <= 0) return;

        List<ChatMessage> forSummary = List.copyOf(messages.subList(0, toSummarise));
        List<ChatMessage> recent = List.copyOf(messages.subList(toSummarise, messages.size()));

        StringBuilder transcript = new StringBuilder(SUMMARY_PROMPT);
        for (ChatMessage m : forSummary) {
            transcript.append(m.role().name()).append(": ").append(m.content()).append("\n");
        }

        LlmRequest req = LlmRequest.builder()
                .model(model)
                .messages(List.of(ChatMessage.user(transcript.toString())))
                .temperature(0.0)
                .maxTokens(512)
                .build();

        LlmResponse response = llmClient.complete(req);
        String summary = response.content().strip();

        messages.clear();
        messages.add(ChatMessage.system("[Summary of prior conversation]\n" + summary));
        messages.addAll(recent);
    }

    public static final class Builder {
        private LlmClient llmClient;
        private String model;
        private int compressAt = 20;
        private int keepRecent = 5;

        private Builder() {}

        public Builder llmClient(LlmClient client) {
            this.llmClient = Objects.requireNonNull(client, "llmClient");
            return this;
        }

        public Builder model(String model) {
            this.model = Objects.requireNonNull(model, "model");
            return this;
        }

        public Builder compressAt(int n) {
            if (n < 2) throw new IllegalArgumentException("compressAt must be >= 2");
            this.compressAt = n;
            return this;
        }

        public Builder keepRecent(int n) {
            if (n < 1) throw new IllegalArgumentException("keepRecent must be >= 1");
            this.keepRecent = n;
            return this;
        }

        public SummaryMemory build() {
            Objects.requireNonNull(llmClient, "llmClient is required");
            Objects.requireNonNull(model, "model is required");
            if (keepRecent >= compressAt) throw new IllegalArgumentException(
                    "keepRecent must be < compressAt");
            return new SummaryMemory(this);
        }
    }
}
