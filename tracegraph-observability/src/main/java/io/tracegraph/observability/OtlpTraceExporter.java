package io.tracegraph.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.tracegraph.core.Status;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceStep;
import io.tracegraph.observability.replay.TraceStore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Offline OTel span emitter for persisted {@link ExecutionTrace}s.
 *
 * <p>Complements the live {@link OtelNodeListener}: while {@code OtelNodeListener} emits spans
 * during execution, {@code OtlpTraceExporter} re-emits spans from any {@link TraceStore} entry —
 * enabling post-hoc export into APM/LLM-observability tools (Langfuse, Arize Phoenix, Jaeger).
 *
 * <p>One root span is emitted per {@link ExecutionTrace} (named by executionId), with one child
 * span per {@link TraceStep}. Timestamps are reconstructed from {@link ExecutionTrace#startedAt()}
 * and per-step {@link TraceStep#duration()} in declaration order (serial approximation for
 * parallel branches).
 */
public final class OtlpTraceExporter {

    private final Tracer tracer;
    private final StateRenderer renderer;

    private OtlpTraceExporter(OpenTelemetry otel, StateRenderer renderer) {
        this.tracer = otel.getTracer(OtelNodeListener.INSTRUMENTATION_NAME);
        this.renderer = renderer;
    }

    /** Creates an exporter backed by the given {@link OpenTelemetry} instance. */
    public static OtlpTraceExporter using(OpenTelemetry otel) {
        return using(otel, StateRenderer.DEFAULT);
    }

    /** Creates an exporter backed by the given {@link OpenTelemetry} instance and state renderer. */
    public static OtlpTraceExporter using(OpenTelemetry otel, StateRenderer renderer) {
        return new OtlpTraceExporter(
                Objects.requireNonNull(otel, "otel"),
                Objects.requireNonNull(renderer, "renderer"));
    }

    /** Creates an exporter backed by the OTel global singleton. */
    public static OtlpTraceExporter usingGlobal() {
        return usingGlobal(StateRenderer.DEFAULT);
    }

    /** Creates an exporter backed by the OTel global singleton and a custom state renderer. */
    public static OtlpTraceExporter usingGlobal(StateRenderer renderer) {
        return new OtlpTraceExporter(
                GlobalOpenTelemetry.get(),
                Objects.requireNonNull(renderer, "renderer"));
    }

    /**
     * Emits one root span plus one child span per step from the given trace.
     *
     * <p>Step timestamps are accumulated serially from {@link ExecutionTrace#startedAt()}.
     */
    public void export(ExecutionTrace<?> trace) {
        Objects.requireNonNull(trace, "trace");
        Span root = startRootSpan(trace);
        Context rootCtx = Context.root().with(root);
        emitSteps(trace.steps(), rootCtx, trace.startedAt());
        endRootSpan(root, trace);
    }

    /**
     * Exports all traces returned by {@link TraceStore#listIds()} from the given store.
     *
     * @return number of traces exported (traces missing from the store are skipped)
     */
    public int exportAll(TraceStore store) {
        Objects.requireNonNull(store, "store");
        int count = 0;
        for (String id : store.listIds()) {
            var opt = store.load(id);
            if (opt.isPresent()) {
                export(opt.get());
                count++;
            }
        }
        return count;
    }

    private Span startRootSpan(ExecutionTrace<?> trace) {
        Span root = tracer.spanBuilder(trace.executionId())
                .setNoParent()
                .setStartTimestamp(toEpochNanos(trace.startedAt()), TimeUnit.NANOSECONDS)
                .startSpan();
        root.setAttribute("tracegraph.execution.id", trace.executionId());
        root.setAttribute("tracegraph.status", trace.status().name());
        root.setAttribute("tracegraph.step.count", (long) trace.steps().size());
        if (trace.isFork()) {
            root.setAttribute("tracegraph.forked.from.execution.id", trace.forkedFromExecutionId());
            root.setAttribute("tracegraph.forked.from.step.index", (long) trace.forkedFromStepIndex());
        }
        return root;
    }

    private void endRootSpan(Span root, ExecutionTrace<?> trace) {
        if (trace.status() != Status.COMPLETED) {
            if (trace.error() != null) {
                root.recordException(trace.error());
            }
            root.setStatus(StatusCode.ERROR, "execution failed");
        } else {
            root.setStatus(StatusCode.OK);
        }
        root.end(toEpochNanos(trace.completedAt()), TimeUnit.NANOSECONDS);
    }

    private Instant emitSteps(List<? extends TraceStep<?>> steps, Context parentCtx, Instant cursor) {
        for (TraceStep<?> step : steps) {
            cursor = emitStep(step, parentCtx, cursor);
        }
        return cursor;
    }

    private Instant emitStep(TraceStep<?> step, Context parentCtx, Instant stepStart) {
        Instant stepEnd = stepStart.plus(step.duration());
        Span span = tracer.spanBuilder(step.nodeName())
                .setParent(parentCtx)
                .setStartTimestamp(toEpochNanos(stepStart), TimeUnit.NANOSECONDS)
                .startSpan();
        span.setAttribute("tracegraph.node.name", step.nodeName());
        span.setAttribute("tracegraph.attempt", (long) step.attempts());
        if (step.before() != null) {
            span.setAttribute("tracegraph.state.before", renderer.render(step.before()));
        }
        if (step.after() != null) {
            span.setAttribute("tracegraph.state.after", renderer.render(step.after()));
        }
        if (step.usage() != null) {
            TraceStep.Usage u = step.usage();
            span.setAttribute(OtelNodeListener.ATTR_LLM_INPUT_TOKENS, (long) u.promptTokens());
            span.setAttribute(OtelNodeListener.ATTR_LLM_OUTPUT_TOKENS, (long) u.completionTokens());
            span.setAttribute(OtelNodeListener.ATTR_LLM_TOTAL_TOKENS, (long) u.totalTokens());
        }
        if (!step.children().isEmpty()) {
            emitSteps(step.children(), Context.root().with(span), stepStart);
        }
        if (step.failed()) {
            span.recordException(step.error());
            span.setStatus(StatusCode.ERROR, step.error().getClass().getSimpleName());
        } else {
            span.setStatus(StatusCode.OK);
        }
        span.end(toEpochNanos(stepEnd), TimeUnit.NANOSECONDS);
        return stepEnd;
    }

    private static long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
