package io.tracegraph.connectors.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphMcpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static GraphMcpServer server() {
        Graph<String> shout = Graph.<String>builder()
                .node("shout", (s, ctx) -> s.toUpperCase(java.util.Locale.ROOT) + "!")
                .entry("shout")
                .terminal("shout")
                .build();
        return GraphMcpServer.builder()
                .serverName("test-agent")
                .serverVersion("1.2.3")
                .tool(McpServerTool.ofGraph("shout", "Shouts the input text",
                        "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}",
                        shout, args -> args.path("text").asText(), s -> s))
                .tool(new McpServerTool("boom", "always fails", null, args -> {
                    throw new IllegalStateException("kaput");
                }))
                .build();
    }

    private static JsonNode rpc(String json) throws Exception {
        return server().handle(MAPPER.readTree(json));
    }

    @Test
    void initializeAdvertisesToolsCapabilityAndServerInfo() throws Exception {
        JsonNode response = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

        assertThat(response.path("id").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(response.path("result").path("capabilities").has("tools")).isTrue();
        assertThat(response.path("result").path("serverInfo").path("name").asText()).isEqualTo("test-agent");
        assertThat(response.path("result").path("serverInfo").path("version").asText()).isEqualTo("1.2.3");
    }

    @Test
    void toolsListExposesRegisteredToolsWithSchemas() throws Exception {
        JsonNode response = rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");

        JsonNode tools = response.path("result").path("tools");
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("shout");
        assertThat(tools.get(0).path("inputSchema").path("properties").has("text")).isTrue();
        assertThat(tools.get(1).path("inputSchema").path("type").asText()).isEqualTo("object");
    }

    @Test
    void toolsCallRunsTheGraphAndReturnsText() throws Exception {
        JsonNode response = rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"shout\",\"arguments\":{\"text\":\"hello\"}}}");

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.path("result").path("content").get(0).path("text").asText()).isEqualTo("HELLO!");
    }

    @Test
    void toolFailureIsAnIsErrorResultNotAProtocolError() throws Exception {
        JsonNode response = rpc("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}");

        assertThat(response.has("error")).isFalse();
        assertThat(response.path("result").path("isError").asBoolean()).isTrue();
        assertThat(response.path("result").path("content").get(0).path("text").asText()).contains("kaput");
    }

    @Test
    void unknownToolAndUnknownMethodAreJsonRpcErrors() throws Exception {
        JsonNode unknownTool = rpc("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nope\"}}");
        JsonNode unknownMethod = rpc("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/list\"}");

        assertThat(unknownTool.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(unknownMethod.path("error").path("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void notificationsProduceNoResponse() throws Exception {
        assertThat(rpc("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).isNull();
    }

    @Test
    void stdioTransportRoundTripsInitializeListAndCall() throws Exception {
        String input = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n"
                + "not json at all\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"shout\",\"arguments\":{\"text\":\"mcp\"}}}\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server().serve(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out);

        String[] lines = out.toString(StandardCharsets.UTF_8).trim().split("\n");
        assertThat(lines).hasSize(3);
        assertThat(MAPPER.readTree(lines[0]).path("result").path("serverInfo").path("name").asText())
                .isEqualTo("test-agent");
        assertThat(MAPPER.readTree(lines[1]).path("result").path("tools")).hasSize(2);
        assertThat(MAPPER.readTree(lines[2]).path("result").path("content").get(0).path("text").asText())
                .isEqualTo("MCP!");
    }

    @Test
    void builderRejectsDuplicateToolsAndEmptyServers() {
        assertThatThrownBy(() -> GraphMcpServer.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphMcpServer.builder()
                .tool(new McpServerTool("a", "", null, args -> ""))
                .tool(new McpServerTool("a", "", null, args -> "")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
