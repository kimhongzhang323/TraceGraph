package io.tracegraph.core;

@FunctionalInterface
public interface Node<S> {
    S execute(S state, Context ctx) throws Exception;
}
