package io.tracegraph.core;

/**
 * A unit of work in a {@link Graph}. Receives the current state {@code S} and returns the next
 * state. The executor may retry a node per the configured {@link RetryPolicy}, so external side
 * effects should use {@link Context#idempotencyKey()} for dedup.
 *
 * <p>Implementations must be thread-safe if reused across executions. Stateless lambdas are typical.
 *
 * @param <S> the graph's state type
 */
@FunctionalInterface
public interface Node<S> {
    S execute(S state, Context ctx) throws Exception;
}
