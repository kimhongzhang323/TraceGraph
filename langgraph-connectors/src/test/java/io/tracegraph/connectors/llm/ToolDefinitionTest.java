package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDefinitionTest {

    @Test
    void basicCreation() {
        var td = ToolDefinition.of("search", "Search the web");
        assertThat(td.name()).isEqualTo("search");
        assertThat(td.description()).isEqualTo("Search the web");
        assertThat(td.parametersSchema()).isEmpty();
    }

    @Test
    void creationWithSchema() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")));
        var td = ToolDefinition.of("search", "Search the web", schema);
        assertThat(td.parametersSchema()).containsKey("type");
    }

    @Test
    void nullNameRejected() {
        assertThatThrownBy(() -> ToolDefinition.of(null, "desc"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankNameRejected() {
        assertThatThrownBy(() -> ToolDefinition.of("  ", "desc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
