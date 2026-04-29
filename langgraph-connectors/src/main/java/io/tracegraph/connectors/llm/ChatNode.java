package io.tracegraph.connectors.llm;

import io.tracegraph.core.Context;
import io.tracegraph.core.Node;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link Node} adapter that bridges any {@link LlmClient} to a graph state type {@code S}.
 * A {@code requestBuilder} turns the current state into an {@link LlmRequest}; a
 * {@code responseFolder} merges the {@link LlmResponse} back into the next state — keeping the
 * bridge between LLM and graph state explicit and testable.
 *
 * <p>Construct via {@link #of(LlmClient, java.util.function.Function, java.util.function.BiFunction)}.
 *
 * @param <S> the graph's state type
 */
public final class ChatNode<S> implements Node<S> {

    private final LlmClient client;
    private final Function<S, LlmRequest> requestBuilder;
    private final BiFunction<S, LlmResponse, S> responseFolder;

    private ChatNode(LlmClient client,
                     Function<S, LlmRequest> requestBuilder,
                     BiFunction<S, LlmResponse, S> responseFolder) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder");
        this.responseFolder = Objects.requireNonNull(responseFolder, "responseFolder");
    }

    public static <S> ChatNode<S> of(LlmClient client,
                                     Function<S, LlmRequest> requestBuilder,
                                     BiFunction<S, LlmResponse, S> responseFolder) {
        return new ChatNode<>(client, requestBuilder, responseFolder);
    }

    @Override
    public S execute(S state, Context ctx) {
        LlmRequest request = requestBuilder.apply(state);
        LlmResponse response = client.complete(request);
        return responseFolder.apply(state, response);
    }
}
