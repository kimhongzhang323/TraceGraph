package io.tracegraph.connectors.llm;

import java.util.List;
import java.util.Objects;

/**
 * A chat message in an LLM conversation.
 *
 * @param role       the role of the message sender
 * @param content    textual content of the message
 * @param toolCallId if role is {@link Role#TOOL}, the call ID this result answers; null otherwise
 * @param toolCalls  if role is {@link Role#ASSISTANT}, tool calls the LLM requested; empty otherwise
 */
public record ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** Backwards-compatible constructor without tool fields. */
    public ChatMessage(Role role, String content) {
        this(role, content, null, List.of());
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    /**
     * Assistant message that includes tool call requests.
     * Used to reconstruct the full conversation history for multi-turn tool calling.
     */
    public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content == null ? "" : content, null, toolCalls);
    }

    /** Tool result message answering a specific tool call. */
    public static ChatMessage toolResult(String callId, String content) {
        return new ChatMessage(Role.TOOL, content, callId, List.of());
    }

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }
}
