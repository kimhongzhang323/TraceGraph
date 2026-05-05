package io.tracegraph.connectors.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tracegraph.connectors.llm.Tool;
import io.tracegraph.connectors.llm.ToolDefinition;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class McpClient {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private McpClient(Builder b) {
        this.baseUri = b.baseUri;
        this.httpClient = b.httpClient != null ? b.httpClient : HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<McpTool> listTools() {
        JsonNode result = call("tools/list", mapper.createObjectNode());
        List<McpTool> tools = new ArrayList<>();
        for (JsonNode t : result.path("tools")) {
            String name = t.path("name").asText();
            String description = t.path("description").asText("");
            String schema = t.path("inputSchema").toString();
            tools.add(new McpTool(name, description, schema));
        }
        return List.copyOf(tools);
    }

    public String callTool(String toolName, String argumentsJson) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        try {
            params.set("arguments", mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson));
        } catch (IOException e) {
            throw new McpException("Invalid arguments JSON", e);
        }
        JsonNode result = call("tools/call", params);
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : result.path("content")) {
            if ("text".equals(item.path("type").asText())) {
                sb.append(item.path("text").asText());
            }
        }
        return sb.toString();
    }

    public List<ToolDefinition> toolDefinitions() {
        List<McpTool> mcpTools = listTools();
        List<ToolDefinition> defs = new ArrayList<>(mcpTools.size());
        TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};
        for (McpTool t : mcpTools) {
            Map<String, Object> schema;
            try {
                schema = mapper.readValue(t.inputSchema(), mapType);
            } catch (IOException e) {
                schema = Map.of();
            }
            defs.add(new ToolDefinition(t.name(), t.description(), schema));
        }
        return List.copyOf(defs);
    }

    public Tool toolAdapter(String toolName) {
        return args -> callTool(toolName, args == null ? "{}" : args);
    }

    private JsonNode call(String method, ObjectNode params) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        body.set("params", params);

        String bodyStr;
        try {
            bodyStr = mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new McpException("Failed to serialize MCP request", e);
        }

        HttpRequest request = HttpRequest.newBuilder(baseUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new McpException("MCP HTTP call failed", e);
        }

        if (response.statusCode() != 200) {
            throw new McpException(response.statusCode(), "MCP server returned " + response.statusCode());
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            if (root.has("error")) {
                JsonNode err = root.get("error");
                throw new McpException(err.path("code").asInt(-32000), err.path("message").asText("unknown error"));
            }
            return root.path("result");
        } catch (IOException e) {
            throw new McpException("Failed to parse MCP response", e);
        }
    }

    public static final class Builder {
        private URI baseUri;
        private HttpClient httpClient;

        private Builder() {}

        public Builder baseUri(String uri) {
            this.baseUri = URI.create(Objects.requireNonNull(uri, "uri"));
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public McpClient build() {
            Objects.requireNonNull(baseUri, "baseUri is required");
            return new McpClient(this);
        }
    }
}
