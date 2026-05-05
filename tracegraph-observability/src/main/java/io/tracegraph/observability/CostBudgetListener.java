package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;

import java.util.concurrent.atomic.AtomicReference;

public final class CostBudgetListener implements NodeListener {
    private final ModelPricing defaultPricing;
    private final double budgetUsd;
    private final AtomicReference<Double> spent = new AtomicReference<>(0.0);

    private CostBudgetListener(ModelPricing defaultPricing,
                                double budgetUsd) {
        this.defaultPricing = defaultPricing;
        this.budgetUsd = budgetUsd;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public void onUsage(String nodeName, int promptTokens, int completionTokens) {
        double increment = defaultPricing.cost(promptTokens, completionTokens);
        double total = spent.updateAndGet(v -> v + increment);
        if (total > budgetUsd) {
            throw new BudgetExceededException(nodeName, total, budgetUsd);
        }
    }

    public double spentUsd() { return spent.get(); }

    public static final class Builder {
        private ModelPricing defaultPricing = new ModelPricing(0.001, 0.002);
        private double budget = Double.MAX_VALUE;

        public Builder defaultPricing(ModelPricing p) {
            defaultPricing = p;
            return this;
        }

        public Builder budgetUsd(double usd) {
            if (usd <= 0) throw new IllegalArgumentException("Budget must be positive");
            budget = usd;
            return this;
        }

        public CostBudgetListener build() {
            return new CostBudgetListener(defaultPricing, budget);
        }
    }
}
