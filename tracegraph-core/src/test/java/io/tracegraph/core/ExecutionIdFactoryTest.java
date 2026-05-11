package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionIdFactoryTest {

    private static Graph<String> singleNodeGraph() {
        return Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .build();
    }

    @Test
    void customFactoryUsedByNoArgRun() {
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .executionIdFactory(() -> "my-id")
                .build();

        ExecutionResult<String> result = graph.run("state");

        assertThat(result.executionId()).isEqualTo("my-id");
    }

    @Test
    void customFactoryUsedByStream() throws InterruptedException {
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s)
                .entry("a")
                .terminal("a")
                .executionIdFactory(() -> "stream-id")
                .build();

        List<NodeEvent<String>> events = new CopyOnWriteArrayList<>();
        Flow.Publisher<NodeEvent<String>> pub = graph.stream("state");
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        pub.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription sub;
            @Override public void onSubscribe(Flow.Subscription s) { this.sub = s; s.request(Long.MAX_VALUE); }
            @Override public void onNext(NodeEvent<String> e) { events.add(e); }
            @Override public void onError(Throwable t) { done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        done.await(5, java.util.concurrent.TimeUnit.SECONDS);

        NodeEvent.Complete<String> complete = events.stream()
                .filter(e -> e instanceof NodeEvent.Complete)
                .map(e -> (NodeEvent.Complete<String>) e)
                .findFirst()
                .orElseThrow();
        assertThat(complete.executionId()).isEqualTo("stream-id");
    }

    @Test
    void defaultFactoryIsUuid() {
        Graph<String> graph = singleNodeGraph();

        ExecutionResult<String> result = graph.run("state");

        assertThat(result.executionId())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
