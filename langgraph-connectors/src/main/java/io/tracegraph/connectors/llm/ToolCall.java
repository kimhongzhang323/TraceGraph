package io.tracegraph.connectors.llm;

import java.util.Objects;

/**
 * A tool invocation requested by the LLM.
 *
 * @param id        provider-assigned call ID (used to correlate results); may be synthetic
 * @param name      tool name matching a {@link ToolDefinition#name()}
 * @param arguments raw JSON string of arguments the LLM wants to pass to the tool
 */
public record ToolCall(String id, String name, String arguments) {

    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
    }
}
