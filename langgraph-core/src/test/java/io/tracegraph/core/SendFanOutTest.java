package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import io.tracegraph.core.exec.NodeExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SendFanOutTest {

    @Test
    void sendAllDispatchesInParallelAndMerges() {
        Graph<Integer> g = Graph.<Integer>builder()
                .routingNode("split", (state, ctx) -> {
                    // Fan out to "double" with three payloads
                    List<Send<Integer>> sends = List.of(
                            Send.to("double", 1),
                            Send.to("double", 2),
                            Send.to("double", 3));
                    // Merger sums the base state + all doubled results
                    return NodeResult.sendAll(sends,
                            (base, results) -> base + results.stream().mapToInt(i -> i).sum(),
                            state);
                })
                .node("double", (s, ctx) -> s * 2)
                .node("done", (s, ctx) -> s)
                .entry("split")
                .terminal("done").terminal("double")
                .edge("split", "done")
                .build();

        ExecutionResult<Integer> result = g.run(0);

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        // base=0, results=[1*2, 2*2, 3*2] = [2, 4, 6], sum = 0 + 12 = 12
        assertThat(result.finalState()).isEqualTo(12);
    }

    @Test
    void sendAllWithNonZeroBase() {
        Graph<Integer> g = Graph.<Integer>builder()
                .routingNode("split", (state, ctx) ->
                        NodeResult.sendAll(
                                List.of(Send.to("inc", 10), Send.to("inc", 20)),
                                (base, results) -> base + results.stream().mapToInt(i -> i).sum(),
                                state))
                .node("inc", (s, ctx) -> s + 1)
                .node("end", (s, ctx) -> s)
                .entry("split").terminal("end").terminal("inc")
                .edge("split", "end")
                .build();

        ExecutionResult<Integer> result = g.run(100);

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        // base=100, results=[11, 21], sum = 100 + 32 = 132
        assertThat(result.finalState()).isEqualTo(132);
    }

    @Test
    void sendRecordFactoryMethod() {
        Send<String> s = Send.to("node_a", "payload");
        assertThat(s.target()).isEqualTo("node_a");
        assertThat(s.payload()).isEqualTo("payload");
    }

    @Test
    void sendAllToUnknownNodeFails() {
        Graph<Integer> g = Graph.<Integer>builder()
                .routingNode("split", (state, ctx) ->
                        NodeResult.sendAll(
                                List.of(Send.to("unknown_node", 1)),
                                (base, results) -> base,
                                state))
                .node("done", (s, ctx) -> s)
                .entry("split").terminal("done")
                .edge("split", "done")
                .build();

        ExecutionResult<Integer> result = g.run(0);

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getMessage()).contains("unknown_node");
    }

    @Test
    void sendAllWithEmptyListFails() {
        Graph<Integer> g = Graph.<Integer>builder()
                .routingNode("split", (state, ctx) ->
                        NodeResult.sendAll(
                                List.of(), // Empty list
                                (base, results) -> base + results.size(),
                                state))
                .node("done", (s, ctx) -> s)
                .entry("split").terminal("done")
                .edge("split", "done")
                .build();

        ExecutionResult<Integer> result = g.run(42);

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(result.error().getCause().getMessage()).isEqualTo("sends must not be empty");
    }

    @Test
    void sendAllToFailingNodePropagatesError() {
        Graph<Integer> g = Graph.<Integer>builder()
                .routingNode("split", (state, ctx) ->
                        NodeResult.sendAll(
                                List.of(Send.to("fail_node", 1)),
                                (base, results) -> base,
                                state))
                .node("fail_node", (s, ctx) -> {
                    throw new RuntimeException("Simulated failure in fan-out");
                })
                .node("done", (s, ctx) -> s)
                .entry("split").terminal("done").terminal("fail_node")
                .edge("split", "done")
                .build();

        ExecutionResult<Integer> result = g.run(0);

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getMessage()).contains("Simulated failure in fan-out");
    }
}
