package io.tracegraph.a2a;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A message exchanged between agents on an {@link AgentBus}.
 */
public record AgentMessage(
        String id,
        String fromId,
        String toId,
        String correlationId,
        Object payload,
        Map<String, String> metadata,
        Instant sentAt) {

    public AgentMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(toId, "toId");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (sentAt == null) sentAt = Instant.now();
    }

    public static AgentMessage of(String fromId, String toId, Object payload) {
        return new AgentMessage(UUID.randomUUID().toString(), fromId, toId,
                null, payload, Map.of(), Instant.now());
    }

    public static AgentMessage reply(AgentMessage original, String fromId, Object payload) {
        return new AgentMessage(UUID.randomUUID().toString(), fromId, original.fromId(),
                original.id(), payload, Map.of(), Instant.now());
    }
}
