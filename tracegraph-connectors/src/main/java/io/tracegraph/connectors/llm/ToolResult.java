package io.tracegraph.connectors.llm;

import java.util.Objects;

/**
 * The outcome of executing a {@link Tool} in response to a {@link ToolCall}.
 *
 * @param callId  the {@link ToolCall#id()} this result answers
 * @param content textual result returned to the LLM as context
 * @param error   {@code true} if tool execution failed; the content then carries the error message
 */
public record ToolResult(String callId, String content, boolean error) {

    public ToolResult {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(content, "content");
    }

    /** Success result. */
    public static ToolResult success(String callId, String content) {
        return new ToolResult(callId, content, false);
    }

    /** Error result. */
    public static ToolResult error(String callId, String message) {
        return new ToolResult(callId, message, true);
    }
}
