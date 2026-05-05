package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class CostBudgetListener implements NodeListener {
    private final Map<String, ModelPricing> pricingByModel;
    private final ModelPricing defaultPricing;
    private final double budgetUsd;
    private final AtomicReference<Double> spent = new AtomicReference<>(0.0);

    private CostBudgetListener(Map<String, ModelPricing> pricingByModel,
                                ModelPricing defaultPricing,
                                double budgetUsd) {
        this.pricingByModel = Map.copyOf(pricingByModel);
        this.defaultPricing = defaultPricing;
        this.budgetUsd = budgetUsd;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public void onUsage(String nodeName, int promptTokens, int completionTokens) {
        ModelPricing pricing = pricingByModel.isEmpty() ? defaultPricing
                : pricingByModel.values().iterator().next();
        double increment = pricing.cost(promptTokens, completionTokens);
        double total = spent.updateAndGet(v -> v + increment);
        if (total > budgetUsd) {
            throw new BudgetExceededException(nodeName, total, budgetUsd);
        }
    }

    public double spentUsd() { return spent.get(); }

    public static final class Builder {
        private final HashMap<String, ModelPricing> pricing = new HashMap<>();
        private ModelPricing defaultPricing = new ModelPricing(0.001, 0.002);
        private double budget = Double.MAX_VALUE;

        public Builder pricing(String modelName, ModelPricing p) {
            pricing.put(modelName, p);
            return this;
        }

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
            return new CostBudgetListener(pricing, defaultPricing, budget);
        }
    }
}
