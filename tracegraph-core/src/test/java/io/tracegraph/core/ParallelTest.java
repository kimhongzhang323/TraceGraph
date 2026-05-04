package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import io.tracegraph.core.spi.NodeListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelTest {

    @Test
    void mergesBranchesInDeclarationOrder() {
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> s + ".0",
                        (s, ctx) -> s + ".1",
                        (s, ctx) -> s + ".2"
                ), (input, results) -> input + "|" + String.join(",", results))
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("seed");

        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.finalState()).isEqualTo("seed|seed.0,seed.1,seed.2");
    }

    @Test
    @Timeout(value = 5)
    void branchesRunConcurrently() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> { latch.countDown(); latch.await(); return "a"; },
                        (s, ctx) -> { latch.countDown(); latch.await(); return "b"; }
                ), (input, results) -> results.get(0) + results.get(1))
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("");

        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.finalState()).isEqualTo("ab");
    }

    @Test
    void firstDeclaredFailureSurfaces() {
        IOException b0 = new IOException("first");
        IOException b2 = new IOException("third");
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> { throw b0; },
                        (s, ctx) -> "ok",
                        (s, ctx) -> { throw b2; }
                ), (input, results) -> "merged")
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("");

        assertThat(r.status()).isEqualTo(Status.FAILED);
        assertThat(r.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(r.error().getCause()).isSameAs(b0);
    }

    @Test
    void parallelAsyncSupportsMixedAsyncBranches() {
        Graph<String> graph = Graph.<String>builder()
                .parallelAsync("fan", List.of(
                        (s, ctx) -> CompletableFuture.completedFuture("a"),
                        (s, ctx) -> CompletableFuture.supplyAsync(() -> "b"),
                        (s, ctx) -> CompletableFuture.completedFuture("c")
                ), (input, results) -> String.join(",", results))
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("");

        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.finalState()).isEqualTo("a,b,c");
    }

    @Test
    void retryOnParallelReRunsAllBranches() {
        AtomicInteger b0Calls = new AtomicInteger();
        AtomicInteger b1Calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> {
                            if (b0Calls.incrementAndGet() < 2) throw new RuntimeException("flaky");
                            return "a";
                        },
                        (s, ctx) -> {
                            b1Calls.incrementAndGet();
                            return "b";
                        }
                ), (input, results) -> results.get(0) + results.get(1),
                   RetryPolicy.fixed(3, Duration.ofMillis(1)))
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("");

        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.finalState()).isEqualTo("ab");
        assertThat(b0Calls).hasValue(2);
        assertThat(b1Calls).hasValue(2);
    }

    @Test
    void parallelWritesOneCheckpointPerCompletion() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> "a",
                        (s, ctx) -> "b",
                        (s, ctx) -> "c"
                ), (input, results) -> String.join("", results))
                .entry("fan")
                .terminal("fan")
                .checkpointStore(store)
                .build();

        graph.run("");

        assertThat(store.all).hasSize(1);
        assertThat(store.all.get(0).lastCompletedNode()).isEqualTo("fan");
        assertThat(store.all.get(0).state()).isEqualTo("abc");
    }

    @Test
    void branchesReceiveSameInputState() {
        AtomicInteger badReads = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> { if (!"in".equals(s)) badReads.incrementAndGet(); return "a"; },
                        (s, ctx) -> { if (!"in".equals(s)) badReads.incrementAndGet(); return "b"; },
                        (s, ctx) -> { if (!"in".equals(s)) badReads.incrementAndGet(); return "c"; }
                ), (input, results) -> input + "|" + String.join("", results))
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("in");

        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(badReads).hasValue(0);
        assertThat(r.finalState()).isEqualTo("in|abc");
    }

    @Test
    void listenerOnlyFiresForParallelName() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onEnter(String name, Object state) { events.add("enter:" + name); }
            @Override public void onExit(String name, Object state)  { events.add("exit:" + name); }
        };
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> "a",
                        (s, ctx) -> "b"
                ), (input, results) -> "ab")
                .entry("fan")
                .terminal("fan")
                .listener(listener)
                .build();

        graph.run("");

        assertThat(events).containsExactly("enter:fan", "exit:fan");
    }

    @Test
    void mergerThrowingFailsTheParallel() {
        IllegalStateException boom = new IllegalStateException("merger broke");
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> "a",
                        (s, ctx) -> "b"
                ), (input, results) -> { throw boom; })
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("");

        assertThat(r.status()).isEqualTo(Status.FAILED);
        assertThat(r.error().getCause()).isSameAs(boom);
    }

    @Test
    void emptyBranchesRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                Graph.<String>builder().parallel("fan", List.of(), (i, r) -> i)
        ).hasMessageContaining("at least one branch");
    }
}
