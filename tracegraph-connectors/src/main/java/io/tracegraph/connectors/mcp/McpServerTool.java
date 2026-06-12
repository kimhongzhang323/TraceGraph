package io.tracegraph.connectors.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.tracegraph.core.Graph;

import java.util.Objects;
import java.util.function.Function;

/**
 * One tool exposed by {@link GraphMcpServer} — name, description, JSON-schema for the arguments,
 * and the handler invoked on {@code tools/call}.
 *
 * @param name            tool name advertised in {@code tools/list}
 * @param description     human/LLM-readable description
 * @param inputSchemaJson JSON schema for the {@code arguments} object, as a JSON string
 * @param handler         maps the parsed {@code arguments} node to the tool's text result
 */
public record McpServerTool(String name, String description, String inputSchemaJson,
                            Function<JsonNode, String> handler) {

    public McpServerTool {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        description = description == null ? "" : description;
        inputSchemaJson = inputSchemaJson == null || inputSchemaJson.isBlank()
                ? "{\"type\":\"object\",\"properties\":{}}" : inputSchemaJson;
        Objects.requireNonNull(handler, "handler");
    }

    /**
     * Exposes a compiled graph as a tool: arguments are decoded to the graph's seed state,
     * the graph runs, and the final state is encoded back to text.
     *
     * @param name            tool name
     * @param description     tool description
     * @param inputSchemaJson JSON schema for the arguments object
     * @param graph           graph to run per call
     * @param seedDecoder     maps the arguments node to the graph's initial state
     * @param resultEncoder   maps the final state to the tool's text result
     * @param <S>             graph state type
     * @return tool wrapping the graph
     */
    public static <S> McpServerTool ofGraph(String name, String description, String inputSchemaJson,
                                            Graph<S> graph,
                                            Function<JsonNode, S> seedDecoder,
                                            Function<S, String> resultEncoder) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(seedDecoder, "seedDecoder");
        Objects.requireNonNull(resultEncoder, "resultEncoder");
        return new McpServerTool(name, description, inputSchemaJson, arguments -> {
            var result = graph.run(seedDecoder.apply(arguments));
            if (result.error() != null) {
                throw new McpException("graph run failed: " + result.error().getMessage(), result.error());
            }
            return resultEncoder.apply(result.finalState());
        });
    }
}
