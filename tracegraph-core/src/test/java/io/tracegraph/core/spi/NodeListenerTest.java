package io.tracegraph.core.spi;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.core.Merger;
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

    @Test
    void firesOnStateOncePerSuccessfulNodeWithBeforeAndAfter() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onState(String name, Object before, Object after) {
                events.add("state:" + name + ":" + before + "->" + after);
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .listener(listener)
                .build();

        graph.run("seed");

        assertThat(events).containsExactly(
                "state:a:seed->seed.a",
                "state:b:seed.a->seed.a.b"
        );
    }

    @Test
    void doesNotFireOnStateForFailingNode() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onState(String name, Object before, Object after) {
                events.add("state:" + name);
            }
            @Override public void onError(String name, Throwable t) {
                events.add("error:" + name);
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("ok",   (s, ctx) -> s + ".ok")
                .node("boom", (s, ctx) -> { throw new RuntimeException("x"); })
                .entry("ok")
                .edge("ok", "boom")
                .terminal("boom")
                .listener(listener)
                .build();

        graph.run("");

        assertThat(events).containsExactly("state:ok", "error:boom");
    }

    @Test
    void onLlmCallDefaultDelegatesToOnUsageForBackCompat() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onUsage(String name, int p, int c) {
                events.add("usage:" + name + ":" + p + "," + c);
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("llm", (s, ctx) -> {
                    ctx.reportLlmCall(new LlmCallInfo("openai", "gpt-4o", 13, 9, "stop"));
                    return s;
                })
                .entry("llm")
                .terminal("llm")
                .listener(listener)
                .build();

        graph.run("");

        assertThat(events).containsExactly("usage:llm:13,9");
    }

    @Test
    void firesOnStateOnceForParallelNotForBranches() {
        List<String> events = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override public void onState(String name, Object before, Object after) {
                events.add("state:" + name + ":" + before + "->" + after);
            }
        };

        Merger<String> merge = (input, results) -> input + "[" + String.join("+", results) + "]";

        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> s + ".x",
                        (s, ctx) -> s + ".y"), merge)
                .entry("fan")
                .terminal("fan")
                .listener(listener)
                .build();

        graph.run("in");

        assertThat(events).containsExactly("state:fan:in->in[in.x+in.y]");
    }
}
