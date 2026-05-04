package io.tracegraph.core;

import io.tracegraph.core.spi.NodeListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTest {

    @Test
    void resumeOfUnknownExecutionIdReturnsEmpty() {
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> "ok")
                .entry("a").terminal("a")
                .checkpointStore(new CheckpointExecutionTest.TestStore())
                .build();

        assertThat(graph.resume("ghost")).isEmpty();
    }

    @Test
    void resumeAfterPartialRunPicksUpFromCheckpoint() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        AtomicBoolean failOnB = new AtomicBoolean(true);

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> {
                    if (failOnB.get()) throw new IllegalStateException("crash");
                    return s + ".b";
                })
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .checkpointStore(store)
                .build();

        ExecutionResult<String> first = graph.run("", "exec-7");
        assertThat(first.status()).isEqualTo(Status.FAILED);
        assertThat(store.latest("exec-7").get().lastCompletedNode()).isEqualTo("a");

        failOnB.set(false);
        Optional<ExecutionResult<String>> resumed = graph.resume("exec-7");

        assertThat(resumed).isPresent();
        ExecutionResult<String> r = resumed.get();
        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.path()).containsExactly("b", "c");
        assertThat(r.finalState()).isEqualTo(".a.b.c");
        assertThat(r.executionId()).isEqualTo("exec-7");
    }

    @Test
    void resumeAfterTerminalCheckpointReturnsCompletedWithEmptyPath() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onEnter(String name, Object state) { events.add("enter:" + name); }
            @Override public void onExit(String name, Object state)  { events.add("exit:" + name); }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("only", (s, ctx) -> s + ".only")
                .entry("only").terminal("only")
                .checkpointStore(store)
                .listener(listener)
                .build();

        graph.run("", "exec-done");
        events.clear();

        Optional<ExecutionResult<String>> resumed = graph.resume("exec-done");

        assertThat(resumed).isPresent();
        assertThat(resumed.get().status()).isEqualTo(Status.COMPLETED);
        assertThat(resumed.get().path()).isEmpty();
        assertThat(resumed.get().finalState()).isEqualTo(".only");
        assertThat(events).isEmpty();
    }

    @Test
    void listenerOnlyFiresForResumedRemainder() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        AtomicBoolean failOnB = new AtomicBoolean(true);

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> {
                    if (failOnB.get()) throw new IllegalStateException("crash");
                    return s + ".b";
                })
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .checkpointStore(store)
                .build();

        graph.run("", "exec-resume");
        failOnB.set(false);

        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onEnter(String name, Object state) { events.add("enter:" + name); }
            @Override public void onExit(String name, Object state)  { events.add("exit:" + name); }
        };
        Graph<String> resumeGraph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .checkpointStore(store)
                .listener(listener)
                .build();

        resumeGraph.resume("exec-resume");

        assertThat(events).containsExactly("enter:b", "exit:b", "enter:c", "exit:c");
    }

    @Test
    void edgePredicateReEvaluatedOnResume() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        AtomicInteger globalSwitch = new AtomicInteger(0);
        AtomicBoolean failOnA = new AtomicBoolean(true);

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> {
                    if (failOnA.get()) throw new IllegalStateException("crash after a");
                    return s + ".a";
                })
                .node("b", (s, ctx) -> s + ".b")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b", st -> globalSwitch.get() == 0)
                .edge("a", "c")
                .terminal("b").terminal("c")
                .checkpointStore(store)
                .build();

        failOnA.set(false);
        graph.run("", "exec-edge");
        assertThat(store.latest("exec-edge").get().lastCompletedNode()).isEqualTo("b");

        store.delete("exec-edge");
        store.save(new Checkpoint<>("exec-edge", "a", "seed.a", java.time.Instant.now(), false));
        globalSwitch.set(1);

        Optional<ExecutionResult<String>> resumed = graph.resume("exec-edge");
        assertThat(resumed).isPresent();
        assertThat(resumed.get().path()).containsExactly("c");
        assertThat(resumed.get().finalState()).isEqualTo("seed.a.c");
    }
}
