package io.tracegraph.core;

import io.tracegraph.core.spi.MemoryStore;
import org.slf4j.Logger;

/**
 * Per-node, per-execution context passed to {@link Node#execute(Object, Context)}. Carries the
 * executionId, the current node name, the retry attempt (0-indexed), an SLF4J {@link Logger}, and
 * a {@link MemoryStore} handle for cross-execution data.
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

    /**
     * Report LLM token usage for this node invocation. The executor forwards this to
     * {@link io.tracegraph.core.spi.NodeListener#onUsage}.
     */
    default void reportUsage(int promptTokens, int completionTokens) {}
}
