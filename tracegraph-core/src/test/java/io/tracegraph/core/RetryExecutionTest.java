package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import io.tracegraph.core.spi.NodeListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetryExecutionTest {

    private static final Duration TINY = Duration.ofMillis(1);

    @Test
    void succeedsAfterTransientFailures() {
        AtomicInteger calls = new AtomicInteger();
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onEnter(String name, Object state) { events.add("enter:" + name); }
            @Override public void onExit(String name, Object state)  { events.add("exit:" + name); }
            @Override public void onError(String name, Throwable t)  { events.add("error:" + name); }
            @Override public void onRetry(String name, int attempt, Throwable t) { events.add("retry:" + name + ":" + attempt); }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 3) throw new IOException("blip");
                    return "ok";
                }, RetryPolicy.fixed(4, TINY))
                .entry("flaky")
                .terminal("flaky")
                .listener(listener)
                .build();

        ExecutionResult<String> result = graph.run("seed");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState()).isEqualTo("ok");
        assertThat(result.path()).containsExactly("flaky");
        assertThat(calls).hasValue(3);
        assertThat(events).containsExactly(
                "enter:flaky",
                "retry:flaky:1",
                "retry:flaky:2",
                "exit:flaky"
        );
    }

    @Test
    void exhaustsRetriesAndFails() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onError(String name, Throwable t)  { events.add("error:" + name); }
            @Override public void onRetry(String name, int attempt, Throwable t) { events.add("retry:" + attempt); }
        };

        IOException last = new IOException("final");
        AtomicInteger calls = new AtomicInteger();

        Graph<String> graph = Graph.<String>builder()
                .node("doomed", (s, ctx) -> {
                    int n = calls.incrementAndGet();
                    throw n == 3 ? last : new IOException("attempt " + n);
                }, RetryPolicy.fixed(3, TINY))
                .entry("doomed")
                .terminal("doomed")
                .listener(listener)
                .build();

        ExecutionResult<String> result = graph.run("seed");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.path()).containsExactly("doomed");
        assertThat(calls).hasValue(3);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getCause()).isSameAs(last);
        assertThat(events).containsExactly("retry:1", "retry:2", "error:doomed");
    }

    @Test
    void nonRetryableExceptionFailsImmediately() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("strict", (s, ctx) -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("nope");
                }, RetryPolicy.fixed(5, TINY).retryOn(t -> t instanceof IOException))
                .entry("strict")
                .terminal("strict")
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void perNodePolicyBeatsDefault() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 4) throw new IOException();
                    return "ok";
                }, RetryPolicy.fixed(5, TINY))
                .entry("flaky")
                .terminal("flaky")
                .defaultRetryPolicy(RetryPolicy.fixed(2, TINY))
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(calls).hasValue(4);
    }

    @Test
    void defaultPolicyAppliesWhenNodeDoesNotSpecify() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 3) throw new IOException();
                    return "ok";
                })
                .entry("flaky")
                .terminal("flaky")
                .defaultRetryPolicy(RetryPolicy.fixed(5, TINY))
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(calls).hasValue(3);
    }

    @Test
    void noPolicyAndNoDefaultMeansSingleAttempt() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("once", (s, ctx) -> {
                    calls.incrementAndGet();
                    throw new IOException();
                })
                .entry("once")
                .terminal("once")
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void contextAttemptIncrementsAcrossRetries() {
        List<Integer> seen = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("track", (s, ctx) -> {
                    seen.add(ctx.attempt());
                    if (calls.incrementAndGet() < 3) throw new IOException();
                    return "ok";
                }, RetryPolicy.fixed(4, TINY))
                .entry("track")
                .terminal("track")
                .build();

        graph.run("");

        assertThat(seen).containsExactly(1, 2, 3);
    }

    @Test
    void idempotencyKeyChangesBetweenAttempts() {
        List<String> keys = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("track", (s, ctx) -> {
                    keys.add(ctx.idempotencyKey());
                    if (calls.incrementAndGet() < 2) throw new IOException();
                    return "ok";
                }, RetryPolicy.fixed(3, TINY))
                .entry("track")
                .terminal("track")
                .build();

        graph.run("");

        assertThat(keys).hasSize(2);
        assertThat(keys.get(0)).endsWith(":track:1");
        assertThat(keys.get(1)).endsWith(":track:2");
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
        String execIdA = keys.get(0).split(":")[0];
        String execIdB = keys.get(1).split(":")[0];
        assertThat(execIdA).isEqualTo(execIdB);
    }
}
