package io.tracegraph.a2a;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDiscoveryTest {

    @Test
    void registersAndListsCardsOrderedById() {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        bus.registerCard(new AgentCard("zeta", "Zeta", "does z", null, List.of("z")));
        bus.registerCard(new AgentCard("alpha", "Alpha", "does a", "https://a.example/a2a", List.of("a", "b")));

        assertThat(bus.agentCards()).extracting(AgentCard::id).containsExactly("alpha", "zeta");
        assertThat(bus.agentCards().get(0).skills()).containsExactly("a", "b");
    }

    @Test
    void reRegisteringTheSameIdReplacesTheCard() {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        bus.registerCard(AgentCard.of("orders"));
        bus.registerCard(new AgentCard("orders", "Orders v2", "lookup + refunds", null, List.of("refunds")));

        assertThat(bus.agentCards()).hasSize(1);
        assertThat(bus.agentCards().get(0).name()).isEqualTo("Orders v2");
    }

    @Test
    void minimalCardDefaultsNameToIdAndEmptyFields() {
        AgentCard card = AgentCard.of("orders");

        assertThat(card.name()).isEqualTo("orders");
        assertThat(card.description()).isEmpty();
        assertThat(card.url()).isNull();
        assertThat(card.skills()).isEmpty();
    }

    @Test
    void blankIdIsRejected() {
        assertThatThrownBy(() -> AgentCard.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void busesWithoutDiscoverySupportDefaultToEmpty() {
        AgentBus minimal = new AgentBus() {
            @Override
            public void send(AgentMessage message) {}

            @Override
            public Runnable subscribe(String agentId, java.util.function.Consumer<AgentMessage> handler) {
                return () -> { };
            }
        };
        minimal.registerCard(AgentCard.of("ignored"));
        assertThat(minimal.agentCards()).isEmpty();
    }
}
