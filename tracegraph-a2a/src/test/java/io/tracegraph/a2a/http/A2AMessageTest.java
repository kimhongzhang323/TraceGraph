package io.tracegraph.a2a.http;

import io.tracegraph.a2a.AgentMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A2AMessageTest {

    @Test
    void fromMapsFieldsCorrectly() {
        AgentMessage msg = new AgentMessage(
                "id-1", "agent-a", "agent-b", "corr-1",
                "hello", Map.of("k", "v"), Instant.now());

        A2AMessage wire = A2AMessage.from(msg);

        assertThat(wire.id()).isEqualTo("id-1");
        assertThat(wire.role()).isEqualTo("agent");
        assertThat(wire.content()).isEqualTo("hello");
        assertThat(wire.taskId()).isEqualTo("corr-1");
        assertThat(wire.metadata()).containsEntry("k", "v");
    }

    @Test
    void fromHandlesNullPayloadAsEmptyString() {
        AgentMessage msg = new AgentMessage(
                "id-2", "agent-a", "agent-b", null,
                null, Map.of(), Instant.now());

        A2AMessage wire = A2AMessage.from(msg);

        assertThat(wire.content()).isEmpty();
    }

    @Test
    void toAgentMessageRoundTrips() {
        A2AMessage wire = new A2AMessage("id-3", "user", "some content", "task-99", Map.of("x", "y"));

        AgentMessage msg = wire.toAgentMessage("from-agent", "to-agent");

        assertThat(msg.id()).isEqualTo("id-3");
        assertThat(msg.fromId()).isEqualTo("from-agent");
        assertThat(msg.toId()).isEqualTo("to-agent");
        assertThat(msg.correlationId()).isEqualTo("task-99");
        assertThat(msg.payload()).isEqualTo("some content");
        assertThat(msg.metadata()).containsEntry("x", "y");
    }

    @Test
    void toAgentMessageHandlesNullMetadata() {
        A2AMessage wire = new A2AMessage("id-4", "user", "payload", null, null);

        AgentMessage msg = wire.toAgentMessage("f", "t");

        assertThat(msg.metadata()).isEmpty();
    }
}
