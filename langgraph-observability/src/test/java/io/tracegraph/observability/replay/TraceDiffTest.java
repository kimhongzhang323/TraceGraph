package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceDiffTest {

    @Test
    void identicalTracesReportNoDivergence() {
        InMemoryTraceStore store = new InMemoryTraceStore();
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a").edge("a", "b").terminal("b")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r1 = g.run("seed");
        ExecutionResult<String> r2 = g.run("seed");

        @SuppressWarnings("unchecked")
        ExecutionTrace<String> t1 = (ExecutionTrace<String>) store.load(r1.executionId()).orElseThrow();
        @SuppressWarnings("unchecked")
        ExecutionTrace<String> t2 = (ExecutionTrace<String>) store.load(r2.executionId()).orElseThrow();

        TraceDiff<String> diff = TraceDiff.between(t1, t2);
        assertThat(diff.divergenceIndex()).isEqualTo(-1);
        assertThat(diff.commonPrefix()).hasSize(2);
        assertThat(diff.leftRemainder()).isEmpty();
        assertThat(diff.rightRemainder()).isEmpty();
        assertThat(diff.sameStatus()).isTrue();
        assertThat(diff.sameFinalState()).isTrue();
        assertThat(diff.identical()).isTrue();
    }

    @Test
    void detectsDivergenceAtFirstDifferingStep() {
        TraceStep<String> sA = step(0, "a", "x", "x.a");
        TraceStep<String> sB1 = step(1, "b", "x.a", "x.a.b");
        TraceStep<String> sB2 = step(1, "b", "x.a", "x.a.B");

        ExecutionTrace<String> left = trace("L", "x", "x.a.b", List.of(sA, sB1));
        ExecutionTrace<String> right = trace("R", "x", "x.a.B", List.of(sA, sB2));

        TraceDiff<String> diff = TraceDiff.between(left, right);
        assertThat(diff.divergenceIndex()).isEqualTo(1);
        assertThat(diff.commonPrefix()).containsExactly(sA);
        assertThat(diff.leftRemainder()).containsExactly(sB1);
        assertThat(diff.rightRemainder()).containsExactly(sB2);
        assertThat(diff.sameFinalState()).isFalse();
        assertThat(diff.identical()).isFalse();
    }

    @Test
    void detectsDivergenceWhenPathsDiffer() {
        TraceStep<String> sA = step(0, "a", "x", "x.a");
        TraceStep<String> sB = step(1, "b", "x.a", "x.a.b");
        TraceStep<String> sC = step(1, "c", "x.a", "x.a.c");

        ExecutionTrace<String> left = trace("L", "x", "x.a.b", List.of(sA, sB));
        ExecutionTrace<String> right = trace("R", "x", "x.a.c", List.of(sA, sC));

        TraceDiff<String> diff = TraceDiff.between(left, right);
        assertThat(diff.divergenceIndex()).isEqualTo(1);
        assertThat(diff.leftRemainder()).extracting(TraceStep::nodeName).containsExactly("b");
        assertThat(diff.rightRemainder()).extracting(TraceStep::nodeName).containsExactly("c");
    }

    @Test
    void detectsTailDivergenceWhenOneIsLonger() {
        TraceStep<String> sA = step(0, "a", "x", "x.a");
        TraceStep<String> sB = step(1, "b", "x.a", "x.a.b");
        TraceStep<String> sC = step(2, "c", "x.a.b", "x.a.b.c");

        ExecutionTrace<String> left = trace("L", "x", "x.a.b", List.of(sA, sB));
        ExecutionTrace<String> right = trace("R", "x", "x.a.b.c", List.of(sA, sB, sC));

        TraceDiff<String> diff = TraceDiff.between(left, right);
        assertThat(diff.divergenceIndex()).isEqualTo(2);
        assertThat(diff.commonPrefix()).hasSize(2);
        assertThat(diff.leftRemainder()).isEmpty();
        assertThat(diff.rightRemainder()).containsExactly(sC);
    }

    @Test
    void differentStatusFlagsSet() {
        TraceStep<String> sA = step(0, "a", "x", "x.a");
        ExecutionTrace<String> left = trace("L", "x", "x.a", Status.COMPLETED, List.of(sA));
        ExecutionTrace<String> right = trace("R", "x", null, Status.FAILED, List.of(sA));

        TraceDiff<String> diff = TraceDiff.between(left, right);
        assertThat(diff.sameStatus()).isFalse();
        assertThat(diff.sameFinalState()).isFalse();
        assertThat(diff.identical()).isFalse();
    }

    private static TraceStep<String> step(int idx, String name, String before, String after) {
        return new TraceStep<>(idx, name, 1, before, after, java.time.Duration.ZERO, null);
    }

    private static ExecutionTrace<String> trace(String id, String in, String out, List<TraceStep<String>> steps) {
        return trace(id, in, out, Status.COMPLETED, steps);
    }

    private static ExecutionTrace<String> trace(String id, String in, String out, Status status, List<TraceStep<String>> steps) {
        return new ExecutionTrace<>(id, in, out, status, null, steps, Instant.EPOCH, Instant.EPOCH);
    }
}
