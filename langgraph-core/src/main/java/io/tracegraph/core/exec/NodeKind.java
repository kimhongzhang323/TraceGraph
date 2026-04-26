package io.tracegraph.core.exec;

import io.tracegraph.core.AsyncNode;
import io.tracegraph.core.Context;
import io.tracegraph.core.Merger;
import io.tracegraph.core.Node;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Internal uniform handle for sync nodes, async nodes, and parallel composites.
 * The executor only sees this — translation happens in the static factories.
 */
public sealed interface NodeKind<S> {

    CompletableFuture<S> invoke(S state, Context ctx, ExecutorService executor);

    static <S> NodeKind<S> sync(Node<S> node) {
        return new Sync<>(node);
    }

    static <S> NodeKind<S> async(AsyncNode<S> node) {
        return new Async<>(node);
    }

    static <S> NodeKind<S> parallel(List<NodeKind<S>> branches, Merger<S> merger) {
        return new Parallel<>(branches, merger);
    }

    record Sync<S>(Node<S> node) implements NodeKind<S> {
        @Override
        public CompletableFuture<S> invoke(S state, Context ctx, ExecutorService executor) {
            try {
                return CompletableFuture.completedFuture(node.execute(state, ctx));
            } catch (Throwable t) {
                CompletableFuture<S> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }
    }

    record Async<S>(AsyncNode<S> node) implements NodeKind<S> {
        @Override
        public CompletableFuture<S> invoke(S state, Context ctx, ExecutorService executor) {
            try {
                return node.executeAsync(state, ctx);
            } catch (Throwable t) {
                CompletableFuture<S> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }
    }

    record Parallel<S>(List<NodeKind<S>> branches, Merger<S> merger) implements NodeKind<S> {
        @Override
        public CompletableFuture<S> invoke(S state, Context ctx, ExecutorService executor) {
            java.util.List<CompletableFuture<S>> futures = new java.util.ArrayList<>(branches.size());
            for (NodeKind<S> branch : branches) {
                CompletableFuture<S> f = CompletableFuture
                        .supplyAsync(() -> branch.invoke(state, ctx, executor), executor)
                        .thenCompose(inner -> inner);
                futures.add(f);
            }
            CompletableFuture<?>[] array = futures.toArray(new CompletableFuture<?>[0]);
            return CompletableFuture.allOf(array).handle((ignore, err) -> {
                for (CompletableFuture<S> f : futures) {
                    if (f.isCompletedExceptionally()) {
                        try {
                            f.join();
                        } catch (CompletionException ce) {
                            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                            sneakyThrow(cause);
                        }
                    }
                }
                java.util.List<S> results = new java.util.ArrayList<>(futures.size());
                for (CompletableFuture<S> f : futures) results.add(f.join());
                return merger.merge(state, java.util.Collections.unmodifiableList(results));
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
