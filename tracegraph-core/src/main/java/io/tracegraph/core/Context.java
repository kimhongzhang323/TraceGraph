package io.tracegraph.core;

import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.VectorStore;
import org.slf4j.Logger;

/**
 * Per-node, per-execution context passed to {@link Node#execute(Object, Context)}. Carries the
 * executionId, the current node name, the retry attempt (0-indexed), an SLF4J {@link Logger}, a
 * {@link MemoryStore} handle for cross-execution data, and a {@link VectorStore} handle for
 * nearest-neighbour recall.
 *
 * <p>{@link #idempotencyKey()} composes the three identifiers into a stable string nodes can use
 * to dedup external side effects across retries. Never share a {@code Context} across executions.
 */
public interface Context {
    String executionId();

    String nodeName();

    int attempt();

    Logger logger();

    default String idempotencyKey() {
        return executionId() + ":" + nodeName() + ":" + attempt();
    }

    default MemoryStore memory() {
        return MemoryStore.noop();
    }

    default VectorStore vectorStore() {
        return VectorStore.noop();
    }

    /**
     * Report LLM token usage for this node invocation. The executor forwards this to
     * {@link io.tracegraph.core.spi.NodeListener#onUsage}.
     */
    default void reportUsage(int promptTokens, int completionTokens) {}

    /**
     * Returns {@code true} when the graph was configured with
     * {@code Graph.Builder.sensitiveDataLogging(true)}, indicating that full LLM prompt and
     * response text should be recorded in the trace. Default is {@code false}.
     */
    default boolean sensitiveDataLoggingEnabled() {
        return false;
    }

    /**
     * Record the raw LLM prompt text and response text for this node invocation. Only forwarded
     * to the {@link io.tracegraph.core.spi.TraceRecorder} when
     * {@link #sensitiveDataLoggingEnabled()} returns {@code true}; otherwise this is a no-op.
     *
     * @param rawInput  full prompt text sent to the LLM
     * @param rawOutput full response text received from the LLM
     */
    default void reportRawIO(String rawInput, String rawOutput) {}
}
