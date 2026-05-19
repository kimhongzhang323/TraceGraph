package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentProfileTest {

    @Test
    void buildsWithRequiredFields() {
        AgentProfile<Object> profile = AgentProfile.<Object>builder()
                .name("researcher")
                .systemPrompt("You research things.")
                .build();

        assertThat(profile.name()).isEqualTo("researcher");
        assertThat(profile.systemPrompt()).isEqualTo("You research things.");
        assertThat(profile.tools()).isEmpty();
        assertThat(profile.toolDefinitions()).isEmpty();
        assertThat(profile.memoryScope()).isNull();
    }

    @Test
    void toolsAndDefinitionsArePairedByBuilder() {
        ToolDefinition def = ToolDefinition.of("search", "Search the web");
        Tool tool = args -> "results";

        AgentProfile<Object> profile = AgentProfile.<Object>builder()
                .name("a")
                .systemPrompt("p")
                .tool(def, tool)
                .build();

        assertThat(profile.tools()).containsExactly(tool);
        assertThat(profile.toolDefinitions()).containsExactly(def);
    }

    @Test
    void canonicalConstructorRejectsMismatchedToolListSizes() {
        ToolDefinition def = ToolDefinition.of("x", "x");
        Tool tool = args -> "";
        assertThatThrownBy(() -> new AgentProfile<>("n", "p", List.of(tool, tool), List.of(def), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same length");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> AgentProfile.<Object>builder()
                .name("  ")
                .systemPrompt("p")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> new AgentProfile<>(null, "p", List.of(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsNullSystemPrompt() {
        assertThatThrownBy(() -> new AgentProfile<>("n", null, List.of(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("systemPrompt");
    }

    @Test
    void memoryScopeIsOptional() {
        java.util.function.Function<String, String> scope = s -> "scoped:" + s;
        AgentProfile<String> profile = AgentProfile.<String>builder()
                .name("a")
                .systemPrompt("p")
                .memoryScope(scope)
                .build();

        assertThat(profile.memoryScope()).isSameAs(scope);
    }

    @Test
    void recordComponentsAreDefensivelyCopied() {
        ToolDefinition def = ToolDefinition.of("x", "x");
        Tool tool = args -> "";
        java.util.ArrayList<Tool> mutableTools = new java.util.ArrayList<>(List.of(tool));
        java.util.ArrayList<ToolDefinition> mutableDefs = new java.util.ArrayList<>(List.of(def));

        AgentProfile<Object> profile = new AgentProfile<>("a", "p", mutableTools, mutableDefs, null);
        mutableTools.clear();
        mutableDefs.clear();

        assertThat(profile.tools()).containsExactly(tool);
        assertThat(profile.toolDefinitions()).containsExactly(def);
    }
}
