package io.tracegraph.core.spi;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeListenerTest {

    @Test
    void firesEnterAndExitInOrder() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onEnter(String name, Object state) { events.add("enter:" + name); }
            @Override public void onExit(String name, Object state)  { events.add("exit:" + name); }
            @Override public void onError(String name, Throwable t)  { events.add("error:" + name); }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .listener(listener)
                .build();

        ExecutionResult<String> result = graph.run("");

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(events).containsExactly(
                "enter:a", "exit:a",
                "enter:b", "exit:b"
        );
    }

    @Test
    void firesOnErrorWhenNodeThrows() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onEnter(String name, Object state) { events.add("enter:" + name); }
            @Override public void onExit(String name, Object state)  { events.add("exit:" + name); }
            @Override public void onError(String name, Throwable t)  { events.add("error:" + name); }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("boom", (s, ctx) -> { throw new RuntimeException("x"); })
                .entry("boom")
                .terminal("boom")
                .listener(listener)
                .build();

        graph.run("");

        assertThat(events).containsExactly("enter:boom", "error:boom");
    }
}
