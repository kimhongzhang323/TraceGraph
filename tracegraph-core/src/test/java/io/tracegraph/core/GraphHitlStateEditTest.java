package io.tracegraph.core;

import io.tracegraph.core.CheckpointExecutionTest.TestStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GraphHitlStateEditTest {

    @Test
    void resumeWithStateOverrideUsesNewState() {
        TestStore store = new TestStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .interruptBefore("b")
                .checkpointStore(store)
                .build();

        g.run("", "eid-hitl-1");

        // Override the state from "A" to "EDITED" before resuming at "b"
        Optional<ExecutionResult<String>> resumed = g.resume("eid-hitl-1", "EDITED");
        assertThat(resumed).isPresent();
        assertThat(resumed.get().status()).isEqualTo(Status.COMPLETED);
        assertThat(resumed.get().finalState()).isEqualTo("EDITEDB");
    }

    @Test
    void resumeWithStateOverrideReturnsEmptyWhenNoCheckpoint() {
        TestStore store = new TestStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .entry("a").terminal("a")
                .checkpointStore(store)
                .build();

        Optional<ExecutionResult<String>> result = g.resume("no-such-eid", "override");
        assertThat(result).isEmpty();
    }

    @Test
    void stateTypeIsEmptyWhenNotConfigured() {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s).entry("a").terminal("a")
                .build();
        assertThat(g.stateType()).isEmpty();
    }

    @Test
    void stateTypeReturnedWhenConfigured() {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s).entry("a").terminal("a")
                .stateType(String.class)
                .build();
        assertThat(g.stateType()).contains(String.class);
    }
}
