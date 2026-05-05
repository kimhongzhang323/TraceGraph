package io.tracegraph.core;

/**
 * A node that chooses the next transition dynamically instead of relying only on statically
 * declared conditional edges.
 *
 * <p>Routing nodes return a {@link NodeResult} that can carry a new state plus an explicit
 * destination or fan-out instruction. They are useful for agent loops, command dispatchers, and
 * other flows where the next step is data-dependent and not known at graph construction time.
 *
 * @param <S> the graph state type
 */
@FunctionalInterface
public interface RoutingNode<S> {
    /**
     * Execute routing logic for the current state.
     *
     * @param state current graph state
     * @param ctx execution context for metadata, logging, memory, and idempotency
     * @return the next state and routing decision
     * @throws Exception any failure that should be surfaced to retry policy and listeners
     */
    NodeResult<S> apply(S state, Context ctx) throws Exception;
}
