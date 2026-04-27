package io.tracegraph.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.tracegraph.core.spi.NodeListener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class OtelNodeListener implements NodeListener {

    static final String INSTRUMENTATION_NAME = "io.tracegraph.observability";

    private static final String ATTR_NODE = "tracegraph.node.name";
    private static final String ATTR_ATTEMPT = "tracegraph.attempt";
    private static final String ATTR_STATE_BEFORE = "tracegraph.state.before";
    private static final String ATTR_STATE_AFTER = "tracegraph.state.after";

    private static final ThreadLocal<Deque<Active>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private final Tracer tracer;
    private final StateRenderer renderer;

    private OtelNodeListener(OpenTelemetry otel, StateRenderer renderer) {
        this.tracer = otel.getTracer(INSTRUMENTATION_NAME);
        this.renderer = renderer;
    }

    public static OtelNodeListener using(OpenTelemetry otel) {
        return using(otel, StateRenderer.DEFAULT);
    }

    public static OtelNodeListener using(OpenTelemetry otel, StateRenderer renderer) {
        return new OtelNodeListener(
                Objects.requireNonNull(otel, "otel"),
                Objects.requireNonNull(renderer, "renderer"));
    }

    public static OtelNodeListener usingGlobal() {
        return usingGlobal(StateRenderer.DEFAULT);
    }

    public static OtelNodeListener usingGlobal(StateRenderer renderer) {
        return new OtelNodeListener(
                GlobalOpenTelemetry.get(),
                Objects.requireNonNull(renderer, "renderer"));
    }

    @Override
    public void onEnter(String nodeName, Object state) {
        Span span = tracer.spanBuilder(nodeName)
                .setAttribute(ATTR_NODE, nodeName)
                .startSpan();
        Scope scope = span.makeCurrent();
        STACK.get().push(new Active(nodeName, span, scope));
    }

    @Override
    public void onExit(String nodeName, Object state) {
        Active a = popMatching(nodeName);
        if (a == null) return;
        a.span.setAttribute(ATTR_ATTEMPT, a.finalAttempt);
        a.span.setStatus(StatusCode.OK);
        a.scope.close();
        a.span.end();
    }

    @Override
    public void onError(String nodeName, Throwable error) {
        Active a = popMatching(nodeName);
        if (a == null) return;
        a.span.setAttribute(ATTR_ATTEMPT, a.finalAttempt);
        a.span.recordException(error);
        a.span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
        a.scope.close();
        a.span.end();
    }

    @Override
    public void onState(String nodeName, Object before, Object after) {
        Active a = STACK.get().peek();
        if (a == null || !a.nodeName.equals(nodeName)) return;
        a.span.addEvent("state", Attributes.builder()
                .put(ATTR_STATE_BEFORE, renderer.render(before))
                .put(ATTR_STATE_AFTER, renderer.render(after))
                .build());
    }

    @Override
    public void onRetry(String nodeName, int attempt, Throwable error) {
        Active a = STACK.get().peek();
        if (a == null || !a.nodeName.equals(nodeName)) return;
        a.span.addEvent("retry", Attributes.builder()
                .put(ATTR_ATTEMPT, attempt)
                .put("exception.type", error.getClass().getName())
                .put("exception.message", String.valueOf(error.getMessage()))
                .build());
        a.finalAttempt = attempt + 1;
    }

    private static Active popMatching(String nodeName) {
        Deque<Active> stack = STACK.get();
        Active top = stack.peek();
        if (top == null || !top.nodeName.equals(nodeName)) return null;
        Active a = stack.pop();
        if (stack.isEmpty()) STACK.remove();
        return a;
    }

    private static final class Active {
        final String nodeName;
        final Span span;
        final Scope scope;
        int finalAttempt = 1;

        Active(String nodeName, Span span, Scope scope) {
            this.nodeName = nodeName;
            this.span = span;
            this.scope = scope;
        }
    }
}
