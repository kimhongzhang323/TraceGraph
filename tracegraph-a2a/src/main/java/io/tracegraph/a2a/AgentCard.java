package io.tracegraph.a2a;

import java.util.List;
import java.util.Objects;

/**
 * Capability descriptor for an agent on the bus — the discovery payload behind
 * {@code GET /a2a/agents}, shaped after the Google A2A agent card (name, description,
 * url, skills).
 *
 * @param id          bus agent id (the {@code toId} peers address messages to)
 * @param name        human-readable display name
 * @param description what the agent does, for humans and routing LLMs
 * @param url         ingress URL peers can POST to; may be {@code null} for in-process-only agents
 * @param skills      capability tags (e.g. {@code "order-lookup"}, {@code "refunds"})
 */
public record AgentCard(String id, String name, String description, String url, List<String> skills) {

    public AgentCard {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        name = name == null ? id : name;
        description = description == null ? "" : description;
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** Minimal card carrying only the bus id. */
    public static AgentCard of(String id) {
        return new AgentCard(id, null, null, null, null);
    }
}
