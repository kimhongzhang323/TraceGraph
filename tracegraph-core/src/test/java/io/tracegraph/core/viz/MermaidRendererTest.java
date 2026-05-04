package io.tracegraph.core.viz;

import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MermaidRendererTest {

    @Test
    void rendersLinearGraph() {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s).node("b", (s, ctx) -> s)
                .edge("a", "b").entry("a").terminal("b").build();
        String mmd = g.toMermaid();
        assertThat(mmd).contains("flowchart TD");
        assertThat(mmd).contains("a --> b");
        assertThat(mmd).contains("[*] --> a");
        assertThat(mmd).contains("b --> [*]");
    }

    @Test
    void rendersConditionalEdgeWithCondLabel() {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s).node("b", (s, ctx) -> s)
                .edge("a", "b", s -> s.startsWith("x"))
                .entry("a").terminal("b").build();
        String mmd = g.toMermaid();
        assertThat(mmd).contains("|cond|");
    }

    @Test
    void rendersSubgraphAsCluster() {
        Graph<String> inner = Graph.<String>builder()
                .node("x", (s, ctx) -> s).entry("x").terminal("x").build();
        Graph<String> outer = Graph.<String>builder()
                .subgraph("nested", inner).entry("nested").terminal("nested").build();
        String mmd = outer.toMermaid();
        assertThat(mmd).contains("subgraph nested");
        assertThat(mmd).contains("end");
    }
}
