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

    /**
     * Called when a node makes an LLM call. Carries provider/system identifier, request model,
     * usage tokens, and the normalized finish reason — sufficient to populate the
     * OpenTelemetry GenAI semantic-convention attributes on a span.
     *
     * <p>The default implementation delegates to {@link #onUsage(String, int, int)} so listeners
     * that only care about token totals keep working unchanged.
     */
    default void onLlmCall(String nodeName, LlmCallInfo info) {
        onUsage(nodeName, info.promptTokens(), info.completionTokens());
    }
}
