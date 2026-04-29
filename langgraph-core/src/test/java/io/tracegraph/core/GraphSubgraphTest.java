package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphSubgraphTest {

    @Test
    void subgraphRunsAsSingleNode() {
        Graph<String> inner = Graph.<String>builder()
                .node("x", (s, ctx) -> s + "X")
                .node("y", (s, ctx) -> s + "Y")
                .edge("x", "y").entry("x").terminal("y")
                .build();
        Graph<String> outer = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .subgraph("nested", inner)
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "nested").edge("nested", "b")
                .entry("a").terminal("b")
                .build();
        assertThat(outer.run("").finalState()).isEqualTo("AXYB");
    }

    @Test
    void subgraphAppearsInNodeNames() {
        Graph<String> inner = Graph.<String>builder()
                .node("x", (s, ctx) -> s).entry("x").terminal("x")
                .build();
        Graph<String> outer = Graph.<String>builder()
                .subgraph("nested", inner).entry("nested").terminal("nested")
                .build();
        assertThat(outer.nodeNames()).contains("nested");
    }

    @Test
    void subgraphAccessorReturnsInnerGraph() {
        Graph<String> inner = Graph.<String>builder()
                .node("x", (s, ctx) -> s).entry("x").terminal("x")
                .build();
        Graph<String> outer = Graph.<String>builder()
                .subgraph("nested", inner).entry("nested").terminal("nested")
                .build();
        assertThat(outer.subgraph("nested")).isPresent();
        assertThat(outer.subgraph("nested").get()).isSameAs(inner);
        assertThat(outer.subgraph("nosuchnode")).isEmpty();
    }

    @Test
    void failingSubgraphSurfacesAsNodeExecutionException() {
        Graph<String> inner = Graph.<String>builder()
                .node("boom", (s, ctx) -> { throw new RuntimeException("inner fail"); })
                .entry("boom").terminal("boom")
                .build();
        Graph<String> outer = Graph.<String>builder()
                .subgraph("sub", inner).entry("sub").terminal("sub")
                .build();
        ExecutionResult<String> r = outer.run("seed");
        assertThat(r.status()).isEqualTo(Status.FAILED);
        assertThat(r.error()).isInstanceOf(NodeExecutionException.class);
    }
}
