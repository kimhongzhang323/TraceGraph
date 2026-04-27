package io.tracegraph.core.spi;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Merger;
import io.tracegraph.core.RetryPolicy;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRecorderTest {

    @Test
    void firesStartEnterExitCompleteForLinearGraph() {
        List<String> events = new ArrayList<>();
        TraceRecorder recorder = new TraceRecorder() {
            @Override public void recordStart(String id, Object s) { events.add("start"); }
            @Override public void recordEnter(String id, String n, int a, Object s) { events.add("enter:" + n); }
            @Override public void recordExit(String id, String n, int attempts, Object before, Object after, long ns) {
                events.add("exit:" + n + ":" + attempts + ":" + before + "->" + after);
            }
            @Override public void recordComplete(String id, Status status, Object finalState) {
                events.add("complete:" + status + ":" + finalState);
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .traceRecorder(recorder)
                .build();

        graph.run("seed");

        assertThat(events).containsExactly(
                "start",
                "enter:a", "exit:a:1:seed->seed.a",
                "enter:b", "exit:b:1:seed.a->seed.a.b",
                "complete:COMPLETED:seed.a.b"
        );
    }

    @Test
    void capturesRetryAttemptsInExit() {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> retries = new ArrayList<>();
        int[] finalAttempts = {0};
        TraceRecorder recorder = new TraceRecorder() {
            @Override public void recordRetry(String id, String n, int attempt, Throwable t) {
                retries.add(attempt);
            }
            @Override public void recordExit(String id, String n, int attempts, Object b, Object a, long ns) {
                finalAttempts[0] = attempts;
            }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 3) throw new IOException("blip");
                    return "ok";
                }, RetryPolicy.fixed(4, Duration.ofMillis(1)))
                .entry("flaky").terminal("flaky")
                .traceRecorder(recorder)
                .build();

        graph.run("");

        assertThat(retries).containsExactly(1, 2);
        assertThat(finalAttempts[0]).isEqualTo(3);
    }

    @Test
    void firesErrorAndCompleteFailedOnFailure() {
        List<String> events = new ArrayList<>();
        TraceRecorder recorder = new TraceRecorder() {
            @Override public void recordEnter(String id, String n, int a, Object s) { events.add("enter:" + n); }
            @Override public void recordExit(String id, String n, int at, Object b, Object a, long ns) { events.add("exit:" + n); }
            @Override public void recordError(String id, String n, Throwable t) { events.add("error:" + n); }
            @Override public void recordComplete(String id, Status status, Object f) { events.add("complete:" + status); }
        };

        Graph<String> graph = Graph.<String>builder()
                .node("ok", (s, ctx) -> s + ".ok")
                .node("boom", (s, ctx) -> { throw new RuntimeException("x"); })
                .entry("ok")
                .edge("ok", "boom")
                .terminal("boom")
                .traceRecorder(recorder)
                .build();

        ExecutionResult<String> r = graph.run("");
        assertThat(r.status()).isEqualTo(Status.FAILED);

        assertThat(events).containsExactly(
                "enter:ok", "exit:ok",
                "enter:boom", "error:boom",
                "complete:FAILED"
        );
    }

    @Test
    void parallelFiresOneEnterAndOneExit() {
        List<String> events = new ArrayList<>();
        TraceRecorder recorder = new TraceRecorder() {
            @Override public void recordEnter(String id, String n, int a, Object s) { events.add("enter:" + n); }
            @Override public void recordExit(String id, String n, int at, Object b, Object a, long ns) {
                events.add("exit:" + n + ":" + b + "->" + a);
            }
        };

        Merger<String> merge = (input, results) -> input + "[" + String.join("+", results) + "]";

        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> s + ".x",
                        (s, ctx) -> s + ".y"), merge)
                .entry("fan").terminal("fan")
                .traceRecorder(recorder)
                .build();

        graph.run("in");

        assertThat(events).containsExactly("enter:fan", "exit:fan:in->in[in.x+in.y]");
    }
}
