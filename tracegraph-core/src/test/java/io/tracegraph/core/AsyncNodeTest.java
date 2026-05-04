package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncNodeTest {

    @Test
    void asyncHappyPath() {
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("a", (s, ctx) -> CompletableFuture.completedFuture(s + ".a"))
                .asyncNode("b", (s, ctx) -> CompletableFuture.completedFuture(s + ".b"))
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .build();

        ExecutionResult<String> result = graph.run("seed");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState()).isEqualTo("seed.a.b");
        assertThat(result.path()).containsExactly("a", "b");
    }

    @Test
    void asyncFailureUnwrapsCompletionException() {
        IOException io = new IOException("network");
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("doomed", (s, ctx) -> CompletableFuture.failedFuture(io))
                .entry("doomed")
                .terminal("doomed")
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getCause()).isSameAs(io);
    }

    @Test
    void asyncWithRetrySucceedsOnLaterAttempt() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 3) {
                        return CompletableFuture.failedFuture(new IOException("blip"));
                    }
                    return CompletableFuture.completedFuture("ok");
                }, RetryPolicy.fixed(4, Duration.ofMillis(1)))
                .entry("flaky")
                .terminal("flaky")
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void asyncWritesCheckpointPerNode() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("a", (s, ctx) -> CompletableFuture.completedFuture("A"))
                .asyncNode("b", (s, ctx) -> CompletableFuture.completedFuture("B"))
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .checkpointStore(store)
                .build();

        graph.run("");

        assertThat(store.all).hasSize(2);
        assertThat(store.all.get(0).lastCompletedNode()).isEqualTo("a");
        assertThat(store.all.get(1).lastCompletedNode()).isEqualTo("b");
        assertThat(store.all.get(1).state()).isEqualTo("B");
    }

    @Test
    void asyncNodeThrowingSynchronouslyIsRetryable() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 2) throw new IllegalStateException("sync throw");
                    return CompletableFuture.completedFuture("ok");
                }, RetryPolicy.fixed(3, Duration.ofMillis(1)))
                .entry("flaky")
                .terminal("flaky")
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(calls).hasValue(2);
    }
}
