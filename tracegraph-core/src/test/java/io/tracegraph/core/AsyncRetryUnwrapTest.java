package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncRetryUnwrapTest {

    @Test
    void errorInsideFailedFutureShortCircuitsRetries() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("oom", (s, ctx) -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new OutOfMemoryError("oom"));
                }, RetryPolicy.fixed(3, Duration.ofMillis(1)))
                .entry("oom")
                .terminal("oom")
                .build();

        ExecutionResult<String> result = graph.run("seed");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getCause()).isInstanceOf(OutOfMemoryError.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void interruptedExceptionInsideFailedFutureShortCircuitsRetries() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .asyncNode("interrupted", (s, ctx) -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new InterruptedException("interrupted"));
                }, RetryPolicy.fixed(3, Duration.ofMillis(1)))
                .entry("interrupted")
                .terminal("interrupted")
                .build();

        ExecutionResult<String> result = graph.run("seed");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(calls.get()).isEqualTo(1);
        Thread.interrupted();
    }
}
