package io.tracegraph.a2a;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAgentBusTest {

    @Test
    void sendDeliversMessageToSubscriber() throws InterruptedException {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        CountDownLatch latch = new CountDownLatch(1);
        List<AgentMessage> received = new CopyOnWriteArrayList<>();

        bus.subscribe("agent-b", msg -> {
            received.add(msg);
            latch.countDown();
        });

        AgentMessage msg = AgentMessage.of("agent-a", "agent-b", "hello");
        bus.send(msg);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEqualTo("hello");
    }

    @Test
    void cancelRemovesSubscriber() throws InterruptedException {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        List<AgentMessage> received = new CopyOnWriteArrayList<>();

        Runnable cancel = bus.subscribe("agent-b", received::add);
        cancel.run();

        AgentMessage msg = AgentMessage.of("agent-a", "agent-b", "hello");
        bus.send(msg);

        // Give virtual thread time to deliver (if subscription was not cancelled)
        Thread.sleep(100);
        assertThat(received).isEmpty();
    }

    @Test
    void multipleSubscribersAllReceiveMessage() throws InterruptedException {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        CountDownLatch latch = new CountDownLatch(2);
        List<AgentMessage> received = new CopyOnWriteArrayList<>();

        bus.subscribe("agent-b", msg -> { received.add(msg); latch.countDown(); });
        bus.subscribe("agent-b", msg -> { received.add(msg); latch.countDown(); });

        bus.send(AgentMessage.of("agent-a", "agent-b", "payload"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);
    }

    @Test
    void sendAndAwaitReceivesReply() throws InterruptedException {
        InMemoryAgentBus bus = new InMemoryAgentBus();

        // responder: listen on "agent-b" and send a reply
        bus.subscribe("agent-b", msg -> {
            AgentMessage reply = AgentMessage.reply(msg, "agent-b", "pong");
            bus.send(reply);
        });

        AgentMessage request = AgentMessage.of("agent-a", "agent-b", "ping");
        AgentMessage reply = bus.sendAndAwait(request, 2000);

        assertThat(reply).isNotNull();
        assertThat(reply.correlationId()).isEqualTo(request.id());
        assertThat(reply.payload()).isEqualTo("pong");
    }

    @Test
    void sendAndAwaitThrowsAgentTimeoutExceptionWhenNoReply() {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        AgentMessage request = AgentMessage.of("agent-a", "agent-b", "ping");

        assertThatThrownBy(() -> bus.sendAndAwait(request, 50))
                .isInstanceOf(AgentTimeoutException.class)
                .hasMessageContaining(request.id());
    }
}
