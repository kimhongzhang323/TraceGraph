package io.tracegraph.observability;

/**
 * Token accounting for a billable LLM exchange, used by {@link LlmCostListener} and
 * {@link CostReport} to expose per-execution and per-node cost breakdowns.
 *
 * <p>This is a separate type from {@code TraceStep.Usage} so cost-report consumers don't pull
 * in the replay-trace model; both records carry the same primitive shape.
 */
public record Usage(int promptTokens, int completionTokens) {

    /** Zero-token usage constant — returned by {@code totalUsage} for unseen executions. */
    public static final Usage ZERO = new Usage(0, 0);

    public Usage {
        if (promptTokens < 0) throw new IllegalArgumentException("promptTokens < 0");
        if (completionTokens < 0) throw new IllegalArgumentException("completionTokens < 0");
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    public Usage plus(Usage other) {
        return new Usage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
    }
}
