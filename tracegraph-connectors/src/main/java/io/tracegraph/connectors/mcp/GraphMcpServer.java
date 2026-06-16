package io.tracegraph.connectors.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MCP server over the stdio transport: exposes registered {@link McpServerTool}s (typically
 * graphs via {@link McpServerTool#ofGraph}) to any MCP client — newline-delimited JSON-RPC 2.0,
 * handling {@code initialize}, {@code tools/list}, and {@code tools/call}.
 *
 * <pre>{@code
 * GraphMcpServer.builder()
 *     .serverName("order-agent")
 *     .tool(McpServerTool.ofGraph("run_order_pipeline", "Runs the order pipeline",
 *             schemaJson, graph, args -> args.path("orderId").asText(), OrderState::summary))
 *     .build()
 *     .serve(System.in, System.out);
 * }</pre>
 *
 * <p>{@link #serve} blocks until the input stream closes. Tool failures are reported as
 * {@code isError: true} tool results (per MCP), protocol errors as JSON-RPC errors.
 */
public final class GraphMcpServer {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final String serverName;
    private final String serverVersion;
    private final Map<String, McpServerTool> tools;
    private final ObjectMapper mapper = new ObjectMapper();

    private GraphMcpServer(Builder b) {
        this.serverName = b.serverName;
        this.serverVersion = b.serverVersion;
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(b.tools));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Reads JSON-RPC messages from {@code in} and writes responses to {@code out} until EOF. */
    public void serve(InputStream in, OutputStream out) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode message;
                try {
                    message = mapper.readTree(line);
                } catch (IOException e) {
                    continue;
                }
                JsonNode response = handle(message);
                if (response != null) {
                    writer.write(mapper.writeValueAsString(response));
                    writer.write('\n');
                    writer.flush();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("MCP stdio transport failed", e);
        }
    }

    /** Handles one JSON-RPC message; returns the response node, or {@code null} for notifications. */
    JsonNode handle(JsonNode message) {
        String method = message.path("method").asText("");
        JsonNode id = message.get("id");
        if (id == null) {
            return null;
        }
        return switch (method) {
            case "initialize" -> result(id, initializeResult());
            case "tools/list" -> result(id, toolsListResult());
            case "tools/call" -> toolsCall(id, message.path("params"));
            case "ping" -> result(id, mapper.createObjectNode());
            default -> error(id, -32601, "method not found: " + method);
        };
    }

    private ObjectNode initializeResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode list = result.putArray("tools");
        for (McpServerTool tool : tools.values()) {
            ObjectNode node = list.addObject();
            node.put("name", tool.name());
            node.put("description", tool.description());
            try {
                node.set("inputSchema", mapper.readTree(tool.inputSchemaJson()));
            } catch (IOException e) {
                node.putObject("inputSchema").put("type", "object");
            }
        }
        return result;
    }

    private JsonNode toolsCall(JsonNode id, JsonNode params) {
        String name = params.path("name").asText("");
        McpServerTool tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "unknown tool: " + name);
        }
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        try {
            String text = tool.handler().apply(params.path("arguments"));
            content.addObject().put("type", "text").put("text", text == null ? "" : text);
            result.put("isError", false);
        } catch (RuntimeException e) {
            content.addObject().put("type", "text")
                    .put("text", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            result.put("isError", true);
        }
        result.set("content", content);
        return result(id, result);
    }

    private JsonNode result(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    /** Builder for {@link GraphMcpServer}. Not thread-safe. */
    public static final class Builder {
        private String serverName = "tracegraph";
        private String serverVersion = "0";
        private final Map<String, McpServerTool> tools = new LinkedHashMap<>();

        private Builder() {}

        public Builder serverName(String serverName) {
            this.serverName = Objects.requireNonNull(serverName, "serverName");
            return this;
        }

        public Builder serverVersion(String serverVersion) {
            this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
            return this;
        }

        public Builder tool(McpServerTool tool) {
            Objects.requireNonNull(tool, "tool");
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("duplicate tool name: " + tool.name());
            }
            return this;
        }

        public GraphMcpServer build() {
            if (tools.isEmpty()) {
                throw new IllegalArgumentException("at least one tool is required");
            }
            return new GraphMcpServer(this);
        }
    }
}
