package io.tracegraph.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.tracegraph.core.spi.NodeListener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@link NodeListener} that bridges graph lifecycle events into Micrometer meters.
 *
 * <p>Three meters are emitted, each tagged with the {@code node} name:
 * <ul>
 *   <li>{@code <prefix>.node.duration} — a {@link Timer} recording each completed node execution
 *       (both successful exit and error path).</li>
 *   <li>{@code <prefix>.node.errors} — a {@link Counter} incremented on {@code onError},
 *       additionally tagged with {@code exception} = simple class name.</li>
 *   <li>{@code <prefix>.llm.tokens} — a {@link Counter} incremented on {@code onUsage},
 *       tagged with {@code type} = {@code prompt} or {@code completion}.</li>
 * </ul>
 *
 * <p>The default prefix is {@code tracegraph}. Customise via {@link #builder(MeterRegistry)}.
 *
 * <p>Active node enters are tracked in a per-thread stack so that nested executions
 * (subgraphs, parallel branches once they fire listener events) pop in LIFO order.
 * Implementation is thread-safe — Micrometer's registry handles concurrent updates and
 * each calling thread has its own stack.
 *
 * <p>Plug in via {@code Graph.Builder.listener(...)} or compose with other listeners via
 * {@link Listeners#compose(NodeListener...)}.
 */
public final class MicrometerNodeListener implements NodeListener {

    private static final String DEFAULT_PREFIX = "tracegraph";

    private static final ThreadLocal<Deque<Active>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private final MeterRegistry registry;
    private final String durationMeter;
    private final String errorMeter;
    private final String tokensMeter;

    /**
     * Creates a listener publishing to {@code registry} under the default {@code tracegraph} prefix.
     */
    public MicrometerNodeListener(MeterRegistry registry) {
        this(registry, DEFAULT_PREFIX);
    }

    private MicrometerNodeListener(MeterRegistry registry, String prefix) {
        this.registry = Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(prefix, "prefix");
        this.durationMeter = prefix + ".node.duration";
        this.errorMeter = prefix + ".node.errors";
        this.tokensMeter = prefix + ".llm.tokens";
    }

    /** Returns a builder for customising the metric prefix. */
    public static Builder builder(MeterRegistry registry) {
        return new Builder(registry);
    }

    @Override
    public void onEnter(String nodeName, Object state) {
        STACK.get().push(new Active(nodeName, System.nanoTime()));
    }

    @Override
    public void onExit(String nodeName, Object state) {
        Active a = popMatching(nodeName);
        if (a == null) return;
        recordDuration(nodeName, a.startNanos);
    }

    @Override
    public void onError(String nodeName, Throwable error) {
        Active a = popMatching(nodeName);
        if (a != null) {
            recordDuration(nodeName, a.startNanos);
        }
        Counter.builder(errorMeter)
                .tag("node", nodeName)
                .tag("exception", error.getClass().getSimpleName())
                .register(registry)
                .increment();
    }

    @Override
    public void onUsage(String nodeName, int promptTokens, int completionTokens) {
        Counter.builder(tokensMeter).tag("node", nodeName).tag("type", "prompt")
                .register(registry).increment(promptTokens);
        Counter.builder(tokensMeter).tag("node", nodeName).tag("type", "completion")
                .register(registry).increment(completionTokens);
    }

    private void recordDuration(String nodeName, long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        Timer.builder(durationMeter)
                .tag("node", nodeName)
                .register(registry)
                .record(elapsed, TimeUnit.NANOSECONDS);
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
        final long startNanos;

        Active(String nodeName, long startNanos) {
            this.nodeName = nodeName;
            this.startNanos = startNanos;
        }
    }

    /** Builder for {@link MicrometerNodeListener}. Not thread-safe. */
    public static final class Builder {
        private final MeterRegistry registry;
        private String prefix = DEFAULT_PREFIX;

        private Builder(MeterRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        /**
         * Sets the meter name prefix. Default is {@code tracegraph}.
         */
        public Builder prefix(String prefix) {
            this.prefix = Objects.requireNonNull(prefix, "prefix");
            return this;
        }

        public MicrometerNodeListener build() {
            return new MicrometerNodeListener(registry, prefix);
        }
    }
}
