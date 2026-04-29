package io.tracegraph.core;

@FunctionalInterface
public interface RoutingNode<S> {
    NodeResult<S> apply(S state, Context ctx) throws Exception;
}
