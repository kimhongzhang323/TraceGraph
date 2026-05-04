package io.tracegraph.connectors.llm;

import java.util.Map;
import java.util.Objects;

/**
 * Metadata describing a tool that the LLM can invoke.
 * <p>
 * {@code parametersSchema} is a JSON-Schema-compatible map describing the expected
 * input to the tool. Most LLM APIs (OpenAI, Anthropic) accept this directly.
 *
 * @param name        unique tool name (must match the key used in the {@link Tool} registry)
 * @param description human-readable description shown to the LLM
 * @param parametersSchema JSON-Schema map for the tool's input; may be empty for no-arg tools
 */
public record ToolDefinition(String name, String description, Map<String, Object> parametersSchema) {

    public ToolDefinition {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        Objects.requireNonNull(description, "description");
        parametersSchema = parametersSchema == null ? Map.of() : Map.copyOf(parametersSchema);
    }

    /** Convenience factory for a tool with no parameters. */
    public static ToolDefinition of(String name, String description) {
        return new ToolDefinition(name, description, Map.of());
    }

    /** Convenience factory with a parameters schema. */
    public static ToolDefinition of(String name, String description, Map<String, Object> parametersSchema) {
        return new ToolDefinition(name, description, parametersSchema);
    }
}
