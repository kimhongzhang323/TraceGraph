package io.tracegraph.e2e;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
class GraphExecutionIT {

    @Test
    void linearGraphRunsToCompletion() {
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "-processed")
                .entry("a")
                .terminal("a")
                .build();

        ExecutionResult<String> result = graph.run("input");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState()).isEqualTo("input-processed");
    }

    @Test
    void multiNodeGraphFollowsEdges() {
        Graph<String> graph = Graph.<String>builder()
                .node("start", (s, ctx) -> s + ":start")
                .node("end", (s, ctx) -> s + ":end")
                .edge("start", "end")
                .entry("start")
                .terminal("end")
                .build();

        ExecutionResult<String> result = graph.run("x");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState()).isEqualTo("x:start:end");
        assertThat(result.path()).containsExactly("start", "end");
    }
}
