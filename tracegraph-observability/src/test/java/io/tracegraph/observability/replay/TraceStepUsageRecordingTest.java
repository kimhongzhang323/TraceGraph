package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.core.spi.TraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceStepUsageRecordingTest {

    @Test
    void recordUsagePopulatesStepUsage() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "seed");
        recorder.recordEnter("eid", "llmNode", 1, "seed");
        recorder.recordUsage("eid", "llmNode", 100, 50);
        recorder.recordExit("eid", "llmNode", 1, "seed", "out", 1_000_000L);
        recorder.recordComplete("eid", Status.COMPLETED, "out");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        TraceStep<String> step = trace.steps().get(0);
        assertThat(step.usage()).isNotNull();
        assertThat(step.usage().promptTokens()).isEqualTo(100);
        assertThat(step.usage().completionTokens()).isEqualTo(50);
    }

    @Test
    void multipleUsageCallsWithinOneNodeAccumulate() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "s");
        recorder.recordEnter("eid", "n", 1, "s");
        recorder.recordUsage("eid", "n", 30, 10);
        recorder.recordUsage("eid", "n", 70, 40);
        recorder.recordExit("eid", "n", 1, "s", "out", 0L);
        recorder.recordComplete("eid", Status.COMPLETED, "out");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        TraceStep.Usage usage = trace.steps().get(0).usage();
        assertThat(usage).isNotNull();
        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(50);
    }

    @Test
    void nonLlmNodeHasNullUsage() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "s");
        recorder.recordEnter("eid", "plain", 1, "s");
        recorder.recordExit("eid", "plain", 1, "s", "out", 0L);
        recorder.recordComplete("eid", Status.COMPLETED, "out");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        assertThat(trace.steps().get(0).usage()).isNull();
    }

    @Test
    void usageClearedBetweenConsecutiveRunsOfSameNode() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder(store);

        recorder.recordStart("eid", "s");
        recorder.recordEnter("eid", "n", 1, "s");
        recorder.recordUsage("eid", "n", 10, 5);
        recorder.recordExit("eid", "n", 1, "s", "mid", 0L);
        recorder.recordEnter("eid", "n", 1, "mid");
        recorder.recordExit("eid", "n", 1, "mid", "out", 0L);
        recorder.recordComplete("eid", Status.COMPLETED, "out");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load("eid").orElseThrow();
        assertThat(trace.steps().get(0).usage()).isNotNull();
        assertThat(trace.steps().get(1).usage()).isNull();
    }

    @Test
    void executionTotalUsageSumsAllSteps() {
        TraceStep<String> s1 = TraceStep.leaf(0, "a", 1, "i", "m", Duration.ZERO, null,
                new TraceStep.Usage(100, 50));
        TraceStep<String> s2 = TraceStep.leaf(1, "b", 1, "m", "f", Duration.ZERO, null);
        TraceStep<String> s3 = TraceStep.leaf(2, "c", 1, "f", "z", Duration.ZERO, null,
                new TraceStep.Usage(200, 80));

        ExecutionTrace<String> trace = new ExecutionTrace<>("x", "i", "z",
                Status.COMPLETED, null, List.of(s1, s2, s3),
                java.time.Instant.now(), java.time.Instant.now());

        TraceStep.Usage total = trace.totalUsage();
        assertThat(total.promptTokens()).isEqualTo(300);
        assertThat(total.completionTokens()).isEqualTo(130);
        assertThat(total.totalTokens()).isEqualTo(430);
    }

    @Test
    void executionTotalUsageIsZeroWhenNoLlmSteps() {
        TraceStep<String> s1 = TraceStep.leaf(0, "a", 1, "i", "f", Duration.ZERO, null);
        ExecutionTrace<String> trace = new ExecutionTrace<>("x", "i", "f",
                Status.COMPLETED, null, List.of(s1),
                java.time.Instant.now(), java.time.Instant.now());

        assertThat(trace.totalUsage()).isEqualTo(TraceStep.Usage.ZERO);
    }

    @Test
    void traceRecorderDefaultRecordUsageIsNoop() {
        TraceRecorder noop = TraceRecorder.noop();
        noop.recordUsage("eid", "node", 10, 5);
    }

    @Test
    void graphRunWithReportingNodePopulatesStepUsage() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> graph = Graph.<String>builder()
                .node("llm", (s, ctx) -> {
                    ctx.reportUsage(120, 60);
                    return s + ".out";
                })
                .entry("llm").terminal("llm")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();

        ExecutionResult<String> result = graph.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> trace = (ExecutionTrace<String>) store.load(result.executionId()).orElseThrow();
        TraceStep.Usage usage = trace.steps().get(0).usage();
        assertThat(usage).isNotNull();
        assertThat(usage.promptTokens()).isEqualTo(120);
        assertThat(usage.completionTokens()).isEqualTo(60);
        assertThat(trace.totalUsage().totalTokens()).isEqualTo(180);
    }
}
