package io.tracegraph.core;

import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous variant of {@link Node}. Implementations start work immediately and return a
 * {@link CompletableFuture} that completes with the next graph state.
 *
 * <p>Use this when the node naturally composes with asynchronous APIs such as HTTP clients,
 * database drivers, or fan-out orchestration. Failures should complete the future exceptionally
 * so the executor can apply retry policy and listener callbacks consistently.
 *
 * @param <S> the graph state type
 */
@FunctionalInterface
public interface AsyncNode<S> {
    /**
     * Begin asynchronous execution for the current state.
     *
     * @param state current graph state
     * @param ctx execution context for metadata, logging, memory, and idempotency
     * @return a future that resolves to the next state
     */
    CompletableFuture<S> executeAsync(S state, Context ctx);
}
