package io.tracegraph.core;

import io.tracegraph.core.exec.GraphValidationException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphInterruptTest {

    @Test
    void interruptBeforePausesExecution() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .interruptBefore("b")
                .checkpointStore(store)
                .build();
        ExecutionResult<String> r = g.run("");
        assertThat(r.status()).isEqualTo(Status.INTERRUPTED);
        assertThat(r.finalState()).isEqualTo("A");
    }

    @Test
    void resumeContinuesPastInterrupt() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .interruptBefore("b")
                .checkpointStore(store)
                .build();
        String eid = "eid-1";
        g.run("", eid);
        Optional<ExecutionResult<String>> resumed = g.resume(eid);
        assertThat(resumed).isPresent();
        ExecutionResult<String> r = resumed.get();
        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.finalState()).isEqualTo("AB");
    }

    @Test
    void interruptAfterPausesAfterNode() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .interruptAfter("a")
                .checkpointStore(store)
                .build();
        ExecutionResult<String> r = g.run("");
        assertThat(r.status()).isEqualTo(Status.INTERRUPTED);
        assertThat(r.finalState()).isEqualTo("A");
    }

    @Test
    void interruptAfterResumeContinues() {
        CheckpointExecutionTest.TestStore store = new CheckpointExecutionTest.TestStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .interruptAfter("a")
                .checkpointStore(store)
                .build();
        String eid = "eid-2";
        g.run("", eid);
        Optional<ExecutionResult<String>> resumed = g.resume(eid);
        assertThat(resumed).isPresent();
        assertThat(resumed.get().status()).isEqualTo(Status.COMPLETED);
        assertThat(resumed.get().finalState()).isEqualTo("AB");
    }

    @Test
    void unknownInterruptBeforeNodeRejected() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a").terminal("a")
                .interruptBefore("ghost")
                .build())
            .isInstanceOf(GraphValidationException.class)
            .hasMessageContaining("ghost");
    }

    @Test
    void unknownInterruptAfterNodeRejected() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a").terminal("a")
                .interruptAfter("ghost")
                .build())
            .isInstanceOf(GraphValidationException.class)
            .hasMessageContaining("ghost");
    }
}
