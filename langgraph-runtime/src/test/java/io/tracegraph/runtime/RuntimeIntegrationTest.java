package io.tracegraph.runtime;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeIntegrationTest {

    @Test
    void crashAndResumeRunsOnlyTheRemainder() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        AtomicInteger nodeBCalls = new AtomicInteger();
        AtomicBoolean crashB = new AtomicBoolean(true);

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> {
                    nodeBCalls.incrementAndGet();
                    if (crashB.get()) throw new RuntimeException("simulated crash");
                    return s + ".b";
                })
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .checkpointStore(store)
                .build();

        ExecutionResult<String> first = graph.run("", "exec-1");
        assertThat(first.status()).isEqualTo(Status.FAILED);
        assertThat(nodeBCalls).hasValue(1);

        crashB.set(false);
        Optional<ExecutionResult<String>> resumed = graph.resume("exec-1");

        assertThat(resumed).isPresent();
        assertThat(resumed.get().status()).isEqualTo(Status.COMPLETED);
        assertThat(resumed.get().finalState()).isEqualTo(".a.b.c");
        assertThat(resumed.get().path()).containsExactly("b", "c");
        assertThat(nodeBCalls).hasValue(2);
    }

    @Test
    void completedExecutionIsResumeIdempotent() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        AtomicInteger calls = new AtomicInteger();

        Graph<String> graph = Graph.<String>builder()
                .node("only", (s, ctx) -> { calls.incrementAndGet(); return s + ".only"; })
                .entry("only").terminal("only")
                .checkpointStore(store)
                .build();

        graph.run("seed", "exec-done");
        assertThat(calls).hasValue(1);

        Optional<ExecutionResult<String>> resumed = graph.resume("exec-done");

        assertThat(resumed).isPresent();
        assertThat(resumed.get().status()).isEqualTo(Status.COMPLETED);
        assertThat(resumed.get().finalState()).isEqualTo("seed.only");
        assertThat(calls).hasValue(1);
    }
}
