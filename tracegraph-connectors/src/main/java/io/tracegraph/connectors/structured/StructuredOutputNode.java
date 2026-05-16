package io.tracegraph.connectors.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.core.Context;
import io.tracegraph.core.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link Node} that calls an {@link LlmClient}, parses the response into a typed value {@code T}
 * via Jackson, and folds the result into the graph state {@code S}. A JSON schema hint is
 * prepended as a system message so models return well-formed JSON. On parse failure the node
 * retries up to {@code maxRetries} times (default 1), appending the error and asking the model
 * to correct itself; after exhausting retries it throws {@link StructuredOutputException}.
 *
 * @param <S> graph state type
 * @param <T> parsed output type
 */
public final class StructuredOutputNode<S, T> implements Node<S> {

    private final LlmClient client;
    private final StructuredOutput<T> structured;
    private final Function<S, LlmRequest> requestFactory;
    private final BiFunction<S, T, S> resultFolder;
    private final int maxRetries;

    private StructuredOutputNode(Builder<S, T> b) {
        this.client = b.client;
        this.structured = b.objectMapper != null
                ? StructuredOutput.of(b.outputType, b.objectMapper)
                : StructuredOutput.of(b.outputType);
        this.requestFactory = b.requestFactory;
        this.resultFolder = b.resultFolder;
        this.maxRetries = b.maxRetries;
    }

    @Override
    public S execute(S state, Context ctx) {
        LlmRequest base = requestFactory.apply(state);

        // Build messages list with schema system prompt prepended
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(
                "Respond with valid JSON only. Schema: " + structured.jsonSchema()));
        messages.addAll(base.messages());

        StructuredOutputException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            LlmRequest req = LlmRequest.builder()
                    .model(base.model())
                    .messages(List.copyOf(messages))
                    .temperature(base.temperature())
                    .maxTokens(base.maxTokens())
                    .tools(base.tools())
                    .build();

            var response = client.complete(req);
            var usage = response.usage();
            if (usage.promptTokens() > 0 || usage.completionTokens() > 0) {
                ctx.reportUsage(usage.promptTokens(), usage.completionTokens());
            }

            try {
                T parsed = structured.extract(response);
                return resultFolder.apply(state, parsed);
            } catch (StructuredOutputException e) {
                lastError = e;
                if (attempt < maxRetries) {
                    messages.add(ChatMessage.assistant(response.content()));
                    messages.add(ChatMessage.user(
                            "Your previous response could not be parsed: " + e.getMessage()
                            + ". Please respond with valid JSON matching the expected schema."));
                }
            }
        }
        throw lastError;
    }

    public static <S, T> Builder<S, T> builder() {
        return new Builder<>();
    }

    public static final class Builder<S, T> {

        private LlmClient client;
        private Class<T> outputType;
        private ObjectMapper objectMapper;
        private Function<S, LlmRequest> requestFactory;
        private BiFunction<S, T, S> resultFolder;
        private int maxRetries = 1;

        private Builder() {}

        public Builder<S, T> client(LlmClient client) {
            this.client = Objects.requireNonNull(client, "client");
            return this;
        }

        public Builder<S, T> outputType(Class<T> outputType) {
            this.outputType = Objects.requireNonNull(outputType, "outputType");
            return this;
        }

        public Builder<S, T> objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            return this;
        }

        public Builder<S, T> requestFactory(Function<S, LlmRequest> requestFactory) {
            this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
            return this;
        }

        public Builder<S, T> resultFolder(BiFunction<S, T, S> resultFolder) {
            this.resultFolder = Objects.requireNonNull(resultFolder, "resultFolder");
            return this;
        }

        public Builder<S, T> maxRetries(int maxRetries) {
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            this.maxRetries = maxRetries;
            return this;
        }

        public StructuredOutputNode<S, T> build() {
            Objects.requireNonNull(client, "client is required");
            Objects.requireNonNull(outputType, "outputType is required");
            Objects.requireNonNull(requestFactory, "requestFactory is required");
            Objects.requireNonNull(resultFolder, "resultFolder is required");
            return new StructuredOutputNode<>(this);
        }
    }
}
