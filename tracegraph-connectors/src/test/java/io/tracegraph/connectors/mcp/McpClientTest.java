package io.tracegraph.connectors.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response;
            if (body.contains("\"tools/list\"")) {
                response = """
                        {"jsonrpc":"2.0","id":"1","result":{"tools":[
                          {"name":"echo","description":"echoes input","inputSchema":{"type":"object"}}
                        ]}}
                        """;
            } else if (body.contains("\"tools/call\"")) {
                response = """
                        {"jsonrpc":"2.0","id":"1","result":{"content":[
                          {"type":"text","text":"echoed: hello"}
                        ]}}
                        """;
            } else {
                response = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void listToolsReturnsMcpTools() {
        McpClient client = McpClient.builder()
                .baseUri("http://localhost:" + port + "/mcp")
                .build();

        List<McpTool> tools = client.listTools();

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("echo");
    }

    @Test
    void callToolReturnsTextContent() {
        McpClient client = McpClient.builder()
                .baseUri("http://localhost:" + port + "/mcp")
                .build();

        String result = client.callTool("echo", "{\"text\":\"hello\"}");

        assertThat(result).isEqualTo("echoed: hello");
    }

    @Test
    void throwsMcpExceptionOnErrorResponse() throws Exception {
        HttpServer errServer = HttpServer.create(new InetSocketAddress(0), 0);
        int errPort = errServer.getAddress().getPort();
        errServer.createContext("/mcp", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        errServer.start();

        McpClient client = McpClient.builder()
                .baseUri("http://localhost:" + errPort + "/mcp")
                .build();

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpException.class);

        errServer.stop(0);
    }
}
