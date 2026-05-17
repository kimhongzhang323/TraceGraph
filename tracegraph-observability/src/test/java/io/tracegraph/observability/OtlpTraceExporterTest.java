package io.tracegraph.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.TraceStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OtlpTraceExporterTest {

    private InMemorySpanExporter spanExporter;
    private OpenTelemetrySdk sdk;
    private OtlpTraceExporter otlpExporter;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                        .build())
                .build();
        otlpExporter = OtlpTraceExporter.using(sdk);
    }

    @AfterEach
    void tearDown() {
        sdk.close();
    }

    @Test
    void emitsRootSpanPlusOneSpanPerStep() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plus(Duration.ofSeconds(2));
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-1", "seed", "done", Status.COMPLETED, null,
                List.of(
                        TraceStep.leaf(0, "nodeA", 1, "seed", "mid", Duration.ofSeconds(1), null),
                        TraceStep.leaf(1, "nodeB", 1, "mid", "done", Duration.ofSeconds(1), null)),
                start, end);

        otlpExporter.export(trace);

        assertThat(spanExporter.getFinishedSpanItems()).hasSize(3);
    }

    @Test
    void rootSpanHasExecutionAttributes() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-42", "s", "s", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "s", "s", Duration.ofMillis(500), null)),
                start, start.plus(Duration.ofMillis(500)));

        otlpExporter.export(trace);

        SpanData root = findRoot(spanExporter.getFinishedSpanItems());
        assertThat(root.getName()).isEqualTo("exec-42");
        assertThat(root.getAttributes().get(AttributeKey.stringKey("tracegraph.execution.id")))
                .isEqualTo("exec-42");
        assertThat(root.getAttributes().get(AttributeKey.stringKey("tracegraph.status")))
                .isEqualTo("COMPLETED");
        assertThat(root.getAttributes().get(AttributeKey.longKey("tracegraph.step.count")))
                .isEqualTo(1L);
    }

    @Test
    void stepSpanHasNodeAttributes() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-1", "before", "after", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "processNode", 3, "before", "after",
                        Duration.ofMillis(100), null)),
                start, start.plus(Duration.ofMillis(100)));

        otlpExporter.export(trace);

        SpanData step = findByName(spanExporter.getFinishedSpanItems(), "processNode");
        assertThat(step).isNotNull();
        assertThat(step.getAttributes().get(AttributeKey.stringKey("tracegraph.node.name")))
                .isEqualTo("processNode");
        assertThat(step.getAttributes().get(AttributeKey.longKey("tracegraph.attempt")))
                .isEqualTo(3L);
    }

    @Test
    void stepSpanIncludesStateBeforeAndAfter() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-1", "seed", "result", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "transform", 1, "seed", "result",
                        Duration.ofMillis(100), null)),
                start, start.plus(Duration.ofMillis(100)));

        otlpExporter.export(trace);

        SpanData step = findByName(spanExporter.getFinishedSpanItems(), "transform");
        assertThat(step.getAttributes().get(AttributeKey.stringKey("tracegraph.state.before")))
                .isEqualTo("seed");
        assertThat(step.getAttributes().get(AttributeKey.stringKey("tracegraph.state.after")))
                .isEqualTo("result");
    }

    @Test
    void failedStepSetsErrorStatusOnStepAndRoot() {
        RuntimeException ex = new RuntimeException("boom");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-fail", "s", null, Status.FAILED, ex,
                List.of(TraceStep.leaf(0, "badNode", 1, "s", null, Duration.ofMillis(50), ex)),
                start, start.plus(Duration.ofMillis(50)));

        otlpExporter.export(trace);

        SpanData step = findByName(spanExporter.getFinishedSpanItems(), "badNode");
        assertThat(step.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        SpanData root = findRoot(spanExporter.getFinishedSpanItems());
        assertThat(root.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void stepSpansAreChildrenOfRoot() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-1", "s", "e", Status.COMPLETED, null,
                List.of(
                        TraceStep.leaf(0, "a", 1, "s", "m", Duration.ofMillis(100), null),
                        TraceStep.leaf(1, "b", 1, "m", "e", Duration.ofMillis(100), null)),
                start, start.plus(Duration.ofMillis(200)));

        otlpExporter.export(trace);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData root = findRoot(spans);
        spans.stream()
                .filter(s -> !s.getSpanId().equals(root.getSpanId()))
                .forEach(child ->
                        assertThat(child.getParentSpanId()).isEqualTo(root.getSpanId()));
    }

    @Test
    void stepSpanTimestampsAccumulateFromTraceStart() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Duration d0 = Duration.ofSeconds(1);
        Duration d1 = Duration.ofSeconds(2);
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-1", "s", "e", Status.COMPLETED, null,
                List.of(
                        TraceStep.leaf(0, "first", 1, "s", "m", d0, null),
                        TraceStep.leaf(1, "second", 1, "m", "e", d1, null)),
                start, start.plus(d0).plus(d1));

        otlpExporter.export(trace);

        SpanData first = findByName(spanExporter.getFinishedSpanItems(), "first");
        SpanData second = findByName(spanExporter.getFinishedSpanItems(), "second");

        long startNanos = start.getEpochSecond() * 1_000_000_000L + start.getNano();
        assertThat(first.getStartEpochNanos()).isEqualTo(startNanos);
        assertThat(first.getEndEpochNanos()).isEqualTo(startNanos + d0.toNanos());
        assertThat(second.getStartEpochNanos()).isEqualTo(startNanos + d0.toNanos());
        assertThat(second.getEndEpochNanos()).isEqualTo(startNanos + d0.toNanos() + d1.toNanos());
    }

    @Test
    void exportAllReturnsTotalCountAndEmitsAllSpans() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        InMemoryTraceStore store = new InMemoryTraceStore();
        store.save(new ExecutionTrace<>(
                "t1", "a", "b", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "a", "b", Duration.ofMillis(10), null)),
                start, start.plus(Duration.ofMillis(10))));
        store.save(new ExecutionTrace<>(
                "t2", "c", "d", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "m", 1, "c", "d", Duration.ofMillis(10), null)),
                start, start.plus(Duration.ofMillis(10))));

        int count = otlpExporter.exportAll(store);

        assertThat(count).isEqualTo(2);
        // 2 traces × (1 root + 1 step) = 4 spans
        assertThat(spanExporter.getFinishedSpanItems()).hasSize(4);
    }

    @Test
    void usageAttributesAppliedToStepSpan() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        TraceStep.Usage usage = new TraceStep.Usage(100, 50);
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "exec-llm", "q", "a", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "llmNode", 1, "q", "a",
                        Duration.ofMillis(200), null, usage)),
                start, start.plus(Duration.ofMillis(200)));

        otlpExporter.export(trace);

        SpanData step = findByName(spanExporter.getFinishedSpanItems(), "llmNode");
        assertThat(step.getAttributes().get(AttributeKey.longKey("llm.usage.input_tokens")))
                .isEqualTo(100L);
        assertThat(step.getAttributes().get(AttributeKey.longKey("llm.usage.output_tokens")))
                .isEqualTo(50L);
        assertThat(step.getAttributes().get(AttributeKey.longKey("llm.usage.total_tokens")))
                .isEqualTo(150L);
    }

    @Test
    void forkLineageAttributesRecordedOnRoot() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionTrace<String> trace = new ExecutionTrace<>(
                "fork-exec", "s", "e", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "s", "e", Duration.ofMillis(10), null)),
                start, start.plus(Duration.ofMillis(10)),
                "parent-exec", 2);

        otlpExporter.export(trace);

        SpanData root = findRoot(spanExporter.getFinishedSpanItems());
        assertThat(root.getAttributes()
                .get(AttributeKey.stringKey("tracegraph.forked.from.execution.id")))
                .isEqualTo("parent-exec");
        assertThat(root.getAttributes()
                .get(AttributeKey.longKey("tracegraph.forked.from.step.index")))
                .isEqualTo(2L);
    }

    // --- helpers ---

    private static SpanData findRoot(List<SpanData> spans) {
        return spans.stream()
                .filter(s -> !s.getParentSpanContext().isValid())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No root span found"));
    }

    private static SpanData findByName(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No span named: " + name));
    }
}
