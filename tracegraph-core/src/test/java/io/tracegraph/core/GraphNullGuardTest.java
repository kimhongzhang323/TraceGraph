package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GraphNullGuardTest {

    private static Graph<String> singleNodeGraph() {
        return Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .build();
    }

    @Test
    void runNullInitialThrows() {
        Graph<String> graph = singleNodeGraph();
        assertThatNullPointerException()
                .isThrownBy(() -> graph.run(null))
                .withMessage("initial state");
    }

    @Test
    void runWithIdNullInitialThrows() {
        Graph<String> graph = singleNodeGraph();
        assertThatNullPointerException()
                .isThrownBy(() -> graph.run(null, "id"))
                .withMessage("initial state");
    }

    @Test
    void streamNullInitialThrows() {
        Graph<String> graph = singleNodeGraph();
        assertThatNullPointerException()
                .isThrownBy(() -> graph.stream(null))
                .withMessage("initial state");
    }
}
