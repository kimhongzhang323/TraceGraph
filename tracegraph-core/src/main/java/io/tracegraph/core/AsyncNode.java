package io.tracegraph.core;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AsyncNode<S> {
    CompletableFuture<S> executeAsync(S state, Context ctx);
}
