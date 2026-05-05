package io.tracegraph.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CostBudgetListenerTest {

    @Test
    void accumulatesSpendWithoutExceedingBudget() {
        ModelPricing pricing = new ModelPricing(1.0, 2.0); // $1 per 1k input, $2 per 1k output
        CostBudgetListener listener = CostBudgetListener.builder()
                .defaultPricing(pricing)
                .budgetUsd(1.0)
                .build();

        // 500 prompt + 100 completion = 0.5 + 0.2 = $0.70
        listener.onUsage("node1", 500, 100);
        assertThat(listener.spentUsd()).isCloseTo(0.70, within(1e-9));
    }

    @Test
    void throwsBudgetExceededExceptionWhenOverBudget() {
        ModelPricing pricing = new ModelPricing(1.0, 2.0);
        CostBudgetListener listener = CostBudgetListener.builder()
                .defaultPricing(pricing)
                .budgetUsd(0.5)
                .build();

        // 1000 prompt tokens = $1.00 which exceeds $0.50 budget
        assertThatThrownBy(() -> listener.onUsage("myNode", 1000, 0))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(ex -> {
                    BudgetExceededException bee = (BudgetExceededException) ex;
                    assertThat(bee.spent()).isGreaterThan(0.5);
                    assertThat(bee.budget()).isCloseTo(0.5, within(1e-9));
                });
    }

    @Test
    void exceptionMessageContainsNodeNameAndAmounts() {
        ModelPricing pricing = new ModelPricing(1.0, 0.0);
        CostBudgetListener listener = CostBudgetListener.builder()
                .defaultPricing(pricing)
                .budgetUsd(0.001)
                .build();

        assertThatThrownBy(() -> listener.onUsage("expensive-node", 1000, 0))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("expensive-node")
                .hasMessageContaining("$")
                .hasMessageContaining("budget");
    }

    @Test
    void budgetExceededExceptionCarriesCorrectFields() {
        BudgetExceededException ex = new BudgetExceededException("testNode", 1.5, 1.0);
        assertThat(ex.spent()).isCloseTo(1.5, within(1e-9));
        assertThat(ex.budget()).isCloseTo(1.0, within(1e-9));
        assertThat(ex.getMessage()).contains("testNode");
    }

    @Test
    void builderRejectsZeroBudget() {
        assertThatThrownBy(() -> CostBudgetListener.builder().budgetUsd(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderRejectsNegativeBudget() {
        assertThatThrownBy(() -> CostBudgetListener.builder().budgetUsd(-1.0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelPricingCostCalculation() {
        ModelPricing pricing = new ModelPricing(2.0, 4.0);
        // 2000 prompt tokens = 2 * $2.0 = $4.0
        // 500 completion tokens = 0.5 * $4.0 = $2.0
        // total = $6.0
        assertThat(pricing.cost(2000, 500)).isCloseTo(6.0, within(1e-9));
    }

    @Test
    void modelPricingCostForZeroTokens() {
        ModelPricing pricing = ModelPricing.gpt4o();
        assertThat(pricing.cost(0, 0)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void modelPricingPresetsAreReasonable() {
        // Smoke test: presets should produce positive costs for non-zero tokens
        assertThat(ModelPricing.gpt4o().cost(1000, 1000)).isPositive();
        assertThat(ModelPricing.gpt4oMini().cost(1000, 1000)).isPositive();
        assertThat(ModelPricing.claude3Haiku().cost(1000, 1000)).isPositive();
        assertThat(ModelPricing.claude3Sonnet().cost(1000, 1000)).isPositive();
    }

    @Test
    void flatPricingScalesCorrectly() {
        // flat(perInputToken, perOutputToken) — per token, not per 1k
        ModelPricing pricing = ModelPricing.flat(0.001, 0.002);
        // 1000 input * 0.001 = $1.0 per 1k (stored as inputPer1kTokens = 1.0)
        assertThat(pricing.inputPer1kTokens()).isCloseTo(1.0, within(1e-9));
        assertThat(pricing.outputPer1kTokens()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void spentAccumulatesAcrossMultipleCalls() {
        ModelPricing pricing = new ModelPricing(1.0, 0.0);
        CostBudgetListener listener = CostBudgetListener.builder()
                .defaultPricing(pricing)
                .budgetUsd(10.0)
                .build();

        listener.onUsage("n1", 1000, 0); // $1.00
        listener.onUsage("n2", 2000, 0); // $2.00
        assertThat(listener.spentUsd()).isCloseTo(3.0, within(1e-9));
    }
}
