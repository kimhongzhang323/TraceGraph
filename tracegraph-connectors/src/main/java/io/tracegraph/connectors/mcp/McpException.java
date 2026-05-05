package io.tracegraph.connectors.mcp;

public final class McpException extends RuntimeException {
    private final int code;

    public McpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
        this.code = -1;
    }

    public int code() { return code; }
}
