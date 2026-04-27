package io.tracegraph.connectors.llm;

import io.tracegraph.core.Context;
import io.tracegraph.core.Node;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

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
