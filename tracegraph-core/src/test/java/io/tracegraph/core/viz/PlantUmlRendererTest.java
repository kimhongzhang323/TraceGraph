package io.tracegraph.core.viz;

import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlantUmlRendererTest {

    @Test
    void rendersLinearGraph() {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s).node("b", (s, ctx) -> s)
                .edge("a", "b").entry("a").terminal("b").build();
        String puml = g.toPlantUml();
        assertThat(puml).startsWith("@startuml");
        assertThat(puml.trim()).endsWith("@enduml");
        assertThat(puml).contains("[*] --> a");
        assertThat(puml).contains("a --> b");
        assertThat(puml).contains("b --> [*]");
    }

    @Test
    void rendersSubgraphAsPackage() {
        Graph<String> inner = Graph.<String>builder()
                .node("x", (s, ctx) -> s).entry("x").terminal("x").build();
        Graph<String> outer = Graph.<String>builder()
                .subgraph("nested", inner).entry("nested").terminal("nested").build();
        String puml = outer.toPlantUml();
        assertThat(puml).contains("package \"nested\"");
        assertThat(puml).startsWith("@startuml");
        assertThat(puml.trim()).endsWith("@enduml");
    }

    @Test
    void rendersConditionalEdgeWithLabel() {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s).node("b", (s, ctx) -> s)
                .edge("a", "b", s -> true)
                .entry("a").terminal("b").build();
        String puml = g.toPlantUml();
        assertThat(puml).contains(" : cond");
    }
}
