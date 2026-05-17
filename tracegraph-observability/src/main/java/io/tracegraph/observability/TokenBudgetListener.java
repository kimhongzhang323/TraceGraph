package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link NodeListener} that enforces per-execution token-count budgets.
 *
 * <p>Accumulates prompt and completion token counts across all {@code onUsage} calls and
 * throws {@link BudgetExceededException} the moment either cumulative total exceeds its
 * configured maximum. Prompt tokens are checked before completion tokens; the first
 * violation wins.
 *
 * <p>Designed to be wired last when composed via {@link Listeners#compose(NodeListener...)}
 * so that tracking listeners (e.g. {@link LlmCostListener}) always record usage even on
 * budget overrun.
 *
 * <p>Thread-safe; safe to reuse across parallel branches within a single execution.
 */
public final class TokenBudgetListener implements NodeListener {

    private final int maxPromptTokens;
    private final int maxCompletionTokens;
    private final AtomicInteger promptTotal = new AtomicInteger(0);
    private final AtomicInteger completionTotal = new AtomicInteger(0);

    /**
     * @param maxPromptTokens     maximum cumulative prompt tokens allowed; must be positive
     * @param maxCompletionTokens maximum cumulative completion tokens allowed; must be positive
     */
    public TokenBudgetListener(int maxPromptTokens, int maxCompletionTokens) {
        if (maxPromptTokens <= 0) {
            throw new IllegalArgumentException("maxPromptTokens must be positive, got: " + maxPromptTokens);
        }
        if (maxCompletionTokens <= 0) {
            throw new IllegalArgumentException("maxCompletionTokens must be positive, got: " + maxCompletionTokens);
        }
        this.maxPromptTokens = maxPromptTokens;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    @Override
    public void onUsage(String nodeName, int promptTokens, int completionTokens) {
        int totalPrompt = promptTotal.addAndGet(promptTokens);
        int totalCompletion = completionTotal.addAndGet(completionTokens);
        if (totalPrompt > maxPromptTokens) {
            throw new BudgetExceededException(nodeName, "prompt", totalPrompt, maxPromptTokens);
        }
        if (totalCompletion > maxCompletionTokens) {
            throw new BudgetExceededException(nodeName, "completion", totalCompletion, maxCompletionTokens);
        }
    }

    /** Returns the cumulative prompt token count recorded so far. */
    public int promptTokensUsed() {
        return promptTotal.get();
    }

    /** Returns the cumulative completion token count recorded so far. */
    public int completionTokensUsed() {
        return completionTotal.get();
    }
}
