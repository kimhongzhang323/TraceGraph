package io.tracegraph.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.RetryPolicy;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OtelNodeListenerTest {

    private InMemorySpanExporter exporter;
    private OpenTelemetrySdk sdk;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() {
        sdk.close();
    }

    @Test
    void singleNodeProducesOneSpan() {
        Graph<String> graph = Graph.<String>builder()
                .node("validate", (s, ctx) -> "ok")
                .entry("validate").terminal("validate")
                .listener(OtelNodeListener.using(sdk))
                .build();

        graph.run("seed");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo("validate");
        assertThat(spans.get(0).getAttributes().asMap())
                .containsEntry(AttributeKey.stringKey("tracegraph.node.name"), "validate");
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    void linearGraphProducesSpansInOrder() {
        Graph<String> graph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .listener(OtelNodeListener.using(sdk))
                .build();

        graph.run("");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName).containsExactly("a", "b", "c");
        assertThat(spans).allSatisfy(s ->
                assertThat(s.getStatus().getStatusCode()).isEqualTo(StatusCode.OK));
    }

    @Test
    void failedNodeRecordsExceptionAndErrorStatus() {
        IOException io = new IOException("network");
        Graph<String> graph = Graph.<String>builder()
                .node("doomed", (s, ctx) -> { throw io; })
                .entry("doomed").terminal("doomed")
                .listener(OtelNodeListener.using(sdk))
                .build();

        ExecutionResult<String> r = graph.run("");

        assertThat(r.status()).isEqualTo(Status.FAILED);
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anySatisfy(ev ->
                assertThat(ev.getAttributes().asMap())
                        .containsEntry(AttributeKey.stringKey("exception.type"),
                                "java.io.IOException"));
    }

    @Test
    void retriedNodeProducesOneSpanWithRetryEvents() {
        AtomicInteger calls = new AtomicInteger();
        Graph<String> graph = Graph.<String>builder()
                .node("flaky", (s, ctx) -> {
                    if (calls.incrementAndGet() < 3) throw new IOException("blip " + calls);
                    return "ok";
                }, RetryPolicy.fixed(4, Duration.ofMillis(1)))
                .entry("flaky").terminal("flaky")
                .listener(OtelNodeListener.using(sdk))
                .build();

        graph.run("");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getEvents()).hasSize(2);
        assertThat(span.getEvents()).allSatisfy(ev ->
                assertThat(ev.getName()).isEqualTo("retry"));
        assertThat(span.getAttributes().asMap())
                .containsEntry(AttributeKey.longKey("tracegraph.attempt"), 3L);
    }

    @Test
    void concurrentExecutionsDoNotLeakScopes() throws Exception {
        OtelNodeListener listener = OtelNodeListener.using(sdk);
        Graph<String> graph = Graph.<String>builder()
                .node("n", (s, ctx) -> "ok")
                .entry("n").terminal("n")
                .listener(listener)
                .build();

        int threads = 16;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger leaks = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        graph.run("");
                        if (Span.current().getSpanContext().isValid()) leaks.incrementAndGet();
                    }
                } catch (Exception e) {
                    leaks.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        assertThat(leaks).hasValue(0);
        assertThat(exporter.getFinishedSpanItems()).hasSize(threads * perThread);
    }
}
