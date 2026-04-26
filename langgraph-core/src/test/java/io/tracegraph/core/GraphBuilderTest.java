package io.tracegraph.core;

import io.tracegraph.core.exec.GraphValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphBuilderTest {

    @Test
    void rejectsMissingEntry() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .terminal("a")
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("entry");
    }

    @Test
    void rejectsEntryNotDeclared() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .terminal("a")
                .entry("missing")
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("not declared");
    }

    @Test
    void rejectsDuplicateNodeName() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .node("a", (s, ctx) -> s))
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsEdgeToUnknownNode() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .edge("a", "ghost")
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void rejectsUnreachableNode() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .node("orphan", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .terminal("orphan")
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void rejectsDeadEnd() {
        assertThatThrownBy(() -> Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .node("b", (s, ctx) -> s)
                .entry("a")
                .edge("a", "b")
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("no outgoing edges");
    }
}
