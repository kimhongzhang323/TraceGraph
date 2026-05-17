package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@link NodeListener} that notifies a handler when a node's wall-clock duration exceeds a
 * configured budget. Mirrors the SLA-style task budgets exposed by CrewAI and other agent
 * frameworks, but stays purely advisory — the handler is invoked, the executor is not interrupted.
 *
 * <p>Budgets are configured per-node (via {@link Builder#budget(String, Duration)}) and/or as a
 * single {@linkplain Builder#defaultBudget(Duration) default budget} applied to any node not in
 * the per-node map. A node with no per-node entry and no default is silently skipped.
 *
 * <p>Active enters are tracked in a per-thread stack so nested executions (subgraphs, parallel
 * branches once they fire listener events) pop in LIFO order. The implementation is thread-safe:
 * each calling thread has its own stack and the budgets map is consulted read-only after build.
 *
 * <p>The handler is invoked on the calling thread; any exception it throws is caught and logged at
 * WARN, never propagated to the executor. Plug in via {@code Graph.Builder.listener(...)} or
 * compose with other listeners via {@link Listeners#compose(NodeListener...)}.
 */
public final class SlowNodeListener implements NodeListener {

    private static final Logger LOG = LoggerFactory.getLogger(SlowNodeListener.class);

    private static final ThreadLocal<Deque<Active>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private final Map<String, Duration> budgets;
    private final Duration defaultBudget;
    private final Consumer<SlowNodeEvent> handler;

    /**
     * Constructs a listener using a per-node budget map and a handler. No default budget — nodes
     * not present in {@code budgets} are ignored.
     */
    public SlowNodeListener(Map<String, Duration> budgets, Consumer<SlowNodeEvent> handler) {
        this(Map.copyOf(Objects.requireNonNull(budgets, "budgets")),
                null,
                Objects.requireNonNull(handler, "handler"));
    }

    private SlowNodeListener(Map<String, Duration> budgets,
                             Duration defaultBudget,
                             Consumer<SlowNodeEvent> handler) {
        this.budgets = budgets;
        this.defaultBudget = defaultBudget;
        this.handler = handler;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void onEnter(String nodeName, Object state) {
        STACK.get().push(new Active(nodeName, System.nanoTime()));
    }

    @Override
    public void onExit(String nodeName, Object state) {
        Active a = popMatching(nodeName);
        if (a != null) checkBudget(nodeName, a.startNanos);
    }

    @Override
    public void onError(String nodeName, Throwable error) {
        Active a = popMatching(nodeName);
        if (a != null) checkBudget(nodeName, a.startNanos);
    }

    private void checkBudget(String nodeName, long startNanos) {
        Duration budget = budgets.getOrDefault(nodeName, defaultBudget);
        if (budget == null) return;
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        if (elapsed.compareTo(budget) <= 0) return;
        try {
            handler.accept(new SlowNodeEvent(nodeName, elapsed, budget));
        } catch (RuntimeException ex) {
            LOG.warn("SlowNodeListener handler threw for node '{}'", nodeName, ex);
        }
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

    /** Builder for {@link SlowNodeListener}. Not thread-safe. */
    public static final class Builder {
        private final Map<String, Duration> budgets = new HashMap<>();
        private Duration defaultBudget;
        private Consumer<SlowNodeEvent> handler;

        private Builder() {}

        /** Registers a budget for a specific node. */
        public Builder budget(String nodeName, Duration budget) {
            Objects.requireNonNull(nodeName, "nodeName");
            requirePositive(budget, "budget");
            budgets.put(nodeName, budget);
            return this;
        }

        /** Sets the budget used for any node not registered via {@link #budget(String, Duration)}. */
        public Builder defaultBudget(Duration budget) {
            requirePositive(budget, "defaultBudget");
            this.defaultBudget = budget;
            return this;
        }

        /** Sets the handler invoked when a node breaches its budget. Required. */
        public Builder handler(Consumer<SlowNodeEvent> handler) {
            this.handler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        public SlowNodeListener build() {
            if (handler == null) {
                throw new IllegalStateException("handler is required");
            }
            return new SlowNodeListener(Map.copyOf(budgets), defaultBudget, handler);
        }

        private static void requirePositive(Duration d, String name) {
            Objects.requireNonNull(d, name);
            if (d.isNegative() || d.isZero()) {
                throw new IllegalArgumentException(name + " must be positive: " + d);
            }
        }
    }
}
