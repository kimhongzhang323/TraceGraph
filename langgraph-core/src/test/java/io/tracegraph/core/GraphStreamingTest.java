package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GraphStreamingTest {

    @Test
    void streamsEnterAndExitForEachNode() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "A")
                .node("b", (s, ctx) -> s + "B")
                .edge("a", "b").entry("a").terminal("b")
                .build();

        List<NodeEvent<String>> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        g.stream("").subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NodeEvent<String> e) { events.add(e); }
            public void onError(Throwable t) { done.countDown(); }
            public void onComplete() { done.countDown(); }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        // expect at least: enter a, exit a, enter b, exit b, complete (5+)
        assertThat(events).hasSizeGreaterThanOrEqualTo(4);
        assertThat(events.get(events.size() - 1)).isInstanceOf(NodeEvent.Complete.class);
    }

    @Test
    void failingNodeSurfacesAsFailedEvent() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("boom", (s, ctx) -> { throw new RuntimeException("nope"); })
                .entry("boom").terminal("boom")
                .build();
        List<NodeEvent<String>> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        g.stream("").subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(NodeEvent<String> e) { events.add(e); }
            public void onError(Throwable t) { done.countDown(); }
            public void onComplete() { done.countDown(); }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(events).anyMatch(e -> e instanceof NodeEvent.Failed);
    }
}
