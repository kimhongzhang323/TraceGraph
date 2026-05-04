package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link NodeListener} that aggregates LLM token usage across an execution.
 * <p>
 * LLM nodes can report usage via {@link #recordUsage(String, int, int)} (called from
 * within a {@code ChatNode} or {@code ReActAgent} response folder). This listener
 * accumulates totals per-execution and per-node, and exposes them via accessors.
 * <p>
 * Thread-safe; designed to be composed with other listeners via
 * {@link Listeners#compose(NodeListener...)}.
 */
public final class LlmCostListener implements NodeListener {

    private final ConcurrentMap<String, UsageCounter> executionTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UsageCounter> nodeTotals = new ConcurrentHashMap<>();

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
