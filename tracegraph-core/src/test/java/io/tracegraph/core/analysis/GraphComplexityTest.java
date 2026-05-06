package io.tracegraph.core.analysis;

import io.tracegraph.core.Graph;
import io.tracegraph.core.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphComplexityTest {

    private static final Node<String> ID = (s, ctx) -> s;

    @Test
    void linearGraph() {
        Graph<String> g = Graph.<String>builder()
                .node("a", ID).node("b", ID).node("c", ID)
                .edge("a", "b").edge("b", "c")
                .entry("a").terminal("c").build();

        GraphComplexity c = g.complexity();

        assertThat(c.nodeCount()).isEqualTo(3);
        assertThat(c.edgeCount()).isEqualTo(2);
        assertThat(c.maxFanOut()).isEqualTo(1);
        assertThat(c.maxDepth()).isEqualTo(2);
        assertThat(c.cyclomaticComplexity()).isEqualTo(1); // no conditional edges → 0+1
        assertThat(c.parallelBranches()).isZero();
        assertThat(c.subgraphDepth()).isZero();
        assertThat(c.hotspots()).isEmpty();
    }

    @Test
    void diamondGraph() {
        // entry → left, right → merge → terminal
        Graph<String> g = Graph.<String>builder()
                .node("entry", ID).node("left", ID).node("right", ID)
                .node("merge", ID).node("end", ID)
                .edge("entry", "left",  s -> s.startsWith("l"))
                .edge("entry", "right", s -> !s.startsWith("l"))
                .edge("left",  "merge")
                .edge("right", "merge")
                .edge("merge", "end")
                .entry("entry").terminal("end").build();

        GraphComplexity c = g.complexity();

        assertThat(c.nodeCount()).isEqualTo(5);
        assertThat(c.edgeCount()).isEqualTo(5);
        assertThat(c.maxFanOut()).isEqualTo(2);           // entry has 2 outgoing edges
        assertThat(c.maxDepth()).isGreaterThanOrEqualTo(3);
        assertThat(c.cyclomaticComplexity()).isEqualTo(3); // 2 conditional edges + 1
        assertThat(c.hotspots()).isEmpty();               // merge has fanIn=2, entry has fanOut=2 — both ≤4
    }

    @Test
    void parallelNode() {
        Graph<String> g = Graph.<String>builder()
                .node("start", ID)
                .parallel("par", List.of(ID, ID, ID), (s, branches) -> branches.get(0))
                .node("end", ID)
                .edge("start", "par").edge("par", "end")
                .entry("start").terminal("end").build();

        GraphComplexity c = g.complexity();

        assertThat(c.parallelBranches()).isEqualTo(3);
        assertThat(c.nodeCount()).isEqualTo(3);
    }

    @Test
    void nestedSubgraph() {
        Graph<String> inner = Graph.<String>builder()
                .node("x", ID).entry("x").terminal("x").build();

        Graph<String> outer = Graph.<String>builder()
                .node("start", ID)
                .subgraph("sub", inner)
                .node("end", ID)
                .edge("start", "sub").edge("sub", "end")
                .entry("start").terminal("end").build();

        GraphComplexity c = outer.complexity();

        assertThat(c.subgraphDepth()).isEqualTo(1);
        assertThat(c.nodeCount()).isEqualTo(3);
    }

    @Test
    void hotspotDetected() {
        // hub node has 5 outgoing edges → hotspot
        Graph<String> g = Graph.<String>builder()
                .node("hub", ID)
                .node("a", ID).node("b", ID).node("c", ID).node("d", ID).node("e", ID)
                .edge("hub", "a").edge("hub", "b").edge("hub", "c")
                .edge("hub", "d").edge("hub", "e")
                .entry("hub").terminal("a").terminal("b").terminal("c").terminal("d").terminal("e")
                .build();

        GraphComplexity c = g.complexity();

        assertThat(c.hotspots()).contains("hub");
        assertThat(c.maxFanOut()).isEqualTo(5);
    }
}
