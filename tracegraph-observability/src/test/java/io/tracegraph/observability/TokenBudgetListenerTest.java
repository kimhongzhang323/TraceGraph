package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TokenBudgetListenerTest {

    @Test
    void accumulatesTokensWithoutExceedingBudget() {
        TokenBudgetListener listener = new TokenBudgetListener(1000, 500);
        listener.onUsage("node1", 400, 200);
        assertThat(listener.promptTokensUsed()).isEqualTo(400);
        assertThat(listener.completionTokensUsed()).isEqualTo(200);
    }

    @Test
    void throwsWhenPromptTokensExceedBudget() {
        TokenBudgetListener listener = new TokenBudgetListener(500, 10000);
        assertThatThrownBy(() -> listener.onUsage("myNode", 600, 0))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(ex -> {
                    BudgetExceededException bee = (BudgetExceededException) ex;
                    assertThat(bee.spent()).isGreaterThan(500);
                    assertThat(bee.budget()).isCloseTo(500, within(1e-9));
                });
    }

    @Test
    void throwsWhenCompletionTokensExceedBudget() {
        TokenBudgetListener listener = new TokenBudgetListener(10000, 200);
        assertThatThrownBy(() -> listener.onUsage("completionNode", 0, 300))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(ex -> {
                    BudgetExceededException bee = (BudgetExceededException) ex;
                    assertThat(bee.spent()).isGreaterThan(200);
                    assertThat(bee.budget()).isCloseTo(200, within(1e-9));
                });
    }

    @Test
    void accumulatesAcrossMultipleCalls() {
        TokenBudgetListener listener = new TokenBudgetListener(1000, 1000);
        listener.onUsage("n1", 300, 100);
        listener.onUsage("n2", 200, 150);
        assertThat(listener.promptTokensUsed()).isEqualTo(500);
        assertThat(listener.completionTokensUsed()).isEqualTo(250);
    }

    @Test
    void doesNotThrowWhenExactlyAtBudget() {
        TokenBudgetListener listener = new TokenBudgetListener(500, 500);
        assertThatNoException().isThrownBy(() -> listener.onUsage("n", 500, 500));
    }

    @Test
    void throwsOnCumulativePromptOverBudget() {
        TokenBudgetListener listener = new TokenBudgetListener(100, 10000);
        listener.onUsage("n1", 50, 0);
        assertThatThrownBy(() -> listener.onUsage("n2", 60, 0))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("n2")
                .hasMessageContaining("prompt");
    }

    @Test
    void throwsOnCumulativeCompletionOverBudget() {
        TokenBudgetListener listener = new TokenBudgetListener(10000, 100);
        listener.onUsage("n1", 0, 50);
        assertThatThrownBy(() -> listener.onUsage("n2", 0, 60))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("n2")
                .hasMessageContaining("completion");
    }

    @Test
    void exceptionMessageContainsNodeNameAndTokenType() {
        TokenBudgetListener listener = new TokenBudgetListener(100, 10000);
        assertThatThrownBy(() -> listener.onUsage("expensive-node", 200, 0))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("expensive-node")
                .hasMessageContaining("prompt");
    }

    @Test
    void constructorRejectsZeroMaxPromptTokens() {
        assertThatThrownBy(() -> new TokenBudgetListener(0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNegativeMaxPromptTokens() {
        assertThatThrownBy(() -> new TokenBudgetListener(-1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsZeroMaxCompletionTokens() {
        assertThatThrownBy(() -> new TokenBudgetListener(100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNegativeMaxCompletionTokens() {
        assertThatThrownBy(() -> new TokenBudgetListener(100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void composedWithLlmCostListenerBothReceiveUsage() {
        LlmCostListener costListener = new LlmCostListener();
        TokenBudgetListener budgetListener = new TokenBudgetListener(10000, 10000);

        NodeListener composed = Listeners.compose(costListener, budgetListener);
        composed.onUsage("n", 100, 50);

        assertThat(budgetListener.promptTokensUsed()).isEqualTo(100);
        assertThat(budgetListener.completionTokensUsed()).isEqualTo(50);
        assertThat(costListener.nodePromptTokens("n")).isEqualTo(100);
        assertThat(costListener.nodeCompletionTokens("n")).isEqualTo(50);
    }

    @Test
    void composedBudgetExceptionPropagatesAfterTrackingListenerRuns() {
        LlmCostListener costListener = new LlmCostListener();
        TokenBudgetListener budgetListener = new TokenBudgetListener(100, 10000);

        NodeListener composed = Listeners.compose(costListener, budgetListener);
        assertThatThrownBy(() -> composed.onUsage("n", 200, 0))
                .isInstanceOf(BudgetExceededException.class);

        assertThat(costListener.nodePromptTokens("n")).isEqualTo(200);
    }
}
