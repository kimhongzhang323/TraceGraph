package io.tracegraph.core.spi;

/**
 * Span-shaped observability hook fired by the executor at node lifecycle boundaries: enter, exit,
 * state diff (one per successful exit), error, retry. Callbacks are executionId-blind by design —
 * thread context carries the execution scope. For executionId-aware capture (replay traces) use
 * {@link TraceRecorder} instead.
 *
 * <p>Plug in via {@code Graph.Builder.listener(...)}; compose multiple via {@code Listeners.compose}.
 * Branches inside {@code parallel(...)} do not fire listener events. Implementations must be
 * thread-safe — Phase 2 listener calls may come from multiple worker threads.
 */
public interface NodeListener {

    NodeListener NOOP = new NodeListener() {};

    default void onEnter(String nodeName, Object state) {}

    default void onExit(String nodeName, Object state) {}

    default void onState(String nodeName, Object before, Object after) {}

    default void onError(String nodeName, Throwable error) {}

    default void onRetry(String nodeName, int attempt, Throwable error) {}

    /**
     * Called when a node consumes LLM tokens.
     */
    default void onUsage(String nodeName, int promptTokens, int completionTokens) {}
}
