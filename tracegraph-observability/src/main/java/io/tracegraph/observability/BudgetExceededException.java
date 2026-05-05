package io.tracegraph.observability;

public final class BudgetExceededException extends RuntimeException {
    private final double spent;
    private final double budget;

    public BudgetExceededException(String nodeName, double spent, double budget) {
        super(String.format("Cost budget exceeded at node '%s': spent $%.6f of $%.6f budget",
                nodeName, spent, budget));
        this.spent = spent;
        this.budget = budget;
    }

    public double spent() { return spent; }
    public double budget() { return budget; }
}
