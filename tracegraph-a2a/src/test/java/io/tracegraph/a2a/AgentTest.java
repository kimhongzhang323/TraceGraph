package io.tracegraph.a2a;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTest {

    private static Graph<String> echoGraph() {
        return Graph.<String>builder()
                .node("echo", (s, ctx) -> s.toUpperCase())
                .entry("echo")
                .terminal("echo")
                .build();
    }

    @Test
    void startProcessesMessageAndCallsReplyHandler() throws InterruptedException {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        Graph<String> graph = echoGraph();

        List<ExecutionResult<String>> results = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Agent<String> agent = Agent.<String>builder()
                .id("my-agent")
                .graph(graph)
                .bus(bus)
                .build();

        agent.start(
                msg -> (String) msg.payload(),
                (msg, result) -> {
                    results.add(result);
                    latch.countDown();
                });

        bus.send(AgentMessage.of("sender", "my-agent", "hello"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).finalState()).isEqualTo("HELLO");
    }

    @Test
    void startCancelStopsReceivingMessages() throws InterruptedException {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        Graph<String> graph = echoGraph();

        List<AgentMessage> received = new CopyOnWriteArrayList<>();

        Agent<String> agent = Agent.<String>builder()
                .id("my-agent")
                .graph(graph)
                .bus(bus)
                .build();

        Runnable cancel = agent.start(
                msg -> (String) msg.payload(),
                (msg, result) -> received.add(msg));

        cancel.run();

        bus.send(AgentMessage.of("sender", "my-agent", "hello"));
        Thread.sleep(100);

        assertThat(received).isEmpty();
    }

    @Test
    void buildWithoutIdThrowsNullPointerException() {
        assertThatThrownBy(() -> Agent.<String>builder()
                .graph(echoGraph())
                .bus(new InMemoryAgentBus())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }
}
