package io.tracegraph.a2a.http;

import io.tracegraph.a2a.AgentMessage;

import java.time.Instant;
import java.util.Map;

/**
 * Wire format record for the Google A2A protocol.
 */
public record A2AMessage(
        String id,
        String role,
        String content,
        String taskId,
        Map<String, String> metadata
) {

    public static A2AMessage from(AgentMessage msg) {
        return new A2AMessage(
                msg.id(),
                "agent",
                msg.payload() == null ? "" : msg.payload().toString(),
                msg.correlationId(),
                msg.metadata());
    }

    public AgentMessage toAgentMessage(String fromId, String toId) {
        return new AgentMessage(
                id,
                fromId,
                toId,
                taskId,
                content,
                metadata == null ? Map.of() : metadata,
                Instant.now());
    }
}
