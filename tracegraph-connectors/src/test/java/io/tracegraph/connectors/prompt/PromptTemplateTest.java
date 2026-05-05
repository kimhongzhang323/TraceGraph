package io.tracegraph.connectors.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    @Test
    void renderReplacesVariablesCorrectly() {
        PromptTemplate t = PromptTemplate.of("test", "1.0", "Hello, {{name}}! Project: {{project}}.");
        String result = t.render(Map.of("name", "Alice", "project", "TraceGraph"));
        assertThat(result).isEqualTo("Hello, Alice! Project: TraceGraph.");
    }

    @Test
    void renderThrowsOnMissingVariable() {
        PromptTemplate t = PromptTemplate.of("test", "1.0", "Hello, {{name}}!");
        assertThatThrownBy(() -> t.render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void checksumIsStableForSameBody() {
        PromptTemplate a = PromptTemplate.of("a", "1.0", "Hello, {{name}}!");
        PromptTemplate b = PromptTemplate.of("b", "2.0", "Hello, {{name}}!");
        assertThat(a.checksum()).isEqualTo(b.checksum());
    }

    @Test
    void checksumDiffersForDifferentBody() {
        PromptTemplate a = PromptTemplate.of("a", "1.0", "Hello!");
        PromptTemplate b = PromptTemplate.of("b", "1.0", "Goodbye!");
        assertThat(a.checksum()).isNotEqualTo(b.checksum());
    }

    @Test
    void ofAutoExtractsVariableNames() {
        PromptTemplate t = PromptTemplate.of("test", "1.0", "{{a}} and {{b}} and {{a}} again");
        assertThat(t.variables()).containsExactly("a", "b");
    }

    @Test
    void renderWithNoVariablesReturnsBodyUnchanged() {
        PromptTemplate t = PromptTemplate.of("test", "1.0", "No variables here.");
        assertThat(t.render(Map.of())).isEqualTo("No variables here.");
    }
}
