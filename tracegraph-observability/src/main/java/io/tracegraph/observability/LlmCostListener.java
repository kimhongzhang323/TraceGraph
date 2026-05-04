package io.tracegraph.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.tracegraph.core.spi.NodeListener;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link NodeListener} that aggregates LLM token usage across an execution.
 * <p>
 * Token usage is automatically captured via {@link #onUsage} when wired into a graph
 * that contains {@code ChatNode} instances. Usage can also be recorded manually via
 * {@link #recordUsage(String, int, int)} and {@link #recordNodeUsage(String, int, int)}.
 * <p>
 * When constructed with a {@link MeterRegistry}, counters are emitted to Micrometer on
 * every usage event. Consumers without Micrometer on the classpath use the no-arg
 * constructor — the Micrometer-aware constructor will throw {@link NoClassDefFoundError}
 * only if Micrometer is absent at runtime.
 * <p>
 * Thread-safe; designed to be composed with other listeners via
 * {@link Listeners#compose(NodeListener...)}.
 */
public final class LlmCostListener implements NodeListener {

    private final ConcurrentMap<String, UsageCounter> executionTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UsageCounter> nodeTotals = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    /** Creates a listener that accumulates in-memory totals only. */
    public LlmCostListener() {
        this.meterRegistry = null;
    }

    /**
     * Creates a listener that accumulates in-memory totals and emits Micrometer counters.
     *
     * @param registry the Micrometer registry to publish metrics to
     */
    public LlmCostListener(MeterRegistry registry) {
        this.meterRegistry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Record token usage for a specific execution. Typically called from within
     * a response folder function after receiving an {@code LlmResponse}.
     *
     * @param executionId      the execution ID
     * @param promptTokens     input tokens consumed
     * @param completionTokens output tokens generated
     */
    public void recordUsage(String executionId, int promptTokens, int completionTokens) {
        executionTotals.computeIfAbsent(executionId, k -> new UsageCounter())
                .add(promptTokens, completionTokens);
        if (meterRegistry != null) {
            meterRegistry.counter("tracegraph.llm.prompt_tokens", "execution_id", executionId)
                    .increment(promptTokens);
            meterRegistry.counter("tracegraph.llm.completion_tokens", "execution_id", executionId)
                    .increment(completionTokens);
        }
    }

    /**
     * Record token usage for a specific node within an execution.
     *
     * @param nodeName         the node name
     * @param promptTokens     input tokens consumed
     * @param completionTokens output tokens generated
     */
    public void recordNodeUsage(String nodeName, int promptTokens, int completionTokens) {
        nodeTotals.computeIfAbsent(nodeName, k -> new UsageCounter())
                .add(promptTokens, completionTokens);
        if (meterRegistry != null) {
            meterRegistry.counter("tracegraph.llm.node.prompt_tokens", "node", nodeName)
                    .increment(promptTokens);
            meterRegistry.counter("tracegraph.llm.node.completion_tokens", "node", nodeName)
                    .increment(completionTokens);
        }
    }

    /** Get aggregated prompt tokens for an execution. */
    public int promptTokens(String executionId) {
        UsageCounter c = executionTotals.get(executionId);
        return c == null ? 0 : c.promptTokens.get();
    }

    /** Get aggregated completion tokens for an execution. */
    public int completionTokens(String executionId) {
        UsageCounter c = executionTotals.get(executionId);
        return c == null ? 0 : c.completionTokens.get();
    }

    /** Get total tokens for an execution. */
    public int totalTokens(String executionId) {
        return promptTokens(executionId) + completionTokens(executionId);
    }

    /** Get aggregated prompt tokens for a node across all executions. */
    public int nodePromptTokens(String nodeName) {
        UsageCounter c = nodeTotals.get(nodeName);
        return c == null ? 0 : c.promptTokens.get();
    }

    /** Get aggregated completion tokens for a node across all executions. */
    public int nodeCompletionTokens(String nodeName) {
        UsageCounter c = nodeTotals.get(nodeName);
        return c == null ? 0 : c.completionTokens.get();
    }

    @Override
    public void onUsage(String nodeName, int promptTokens, int completionTokens) {
        recordNodeUsage(nodeName, promptTokens, completionTokens);
    }

    @Override public void onEnter(String nodeName, Object state) {}
    @Override public void onExit(String nodeName, Object state) {}
    @Override public void onState(String nodeName, Object before, Object after) {}
    @Override public void onRetry(String nodeName, int attempt, Throwable error) {}
    @Override public void onError(String nodeName, Throwable error) {}

    private static final class UsageCounter {
        final AtomicInteger promptTokens = new AtomicInteger();
        final AtomicInteger completionTokens = new AtomicInteger();

        void add(int prompt, int completion) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
        }
    }
}
