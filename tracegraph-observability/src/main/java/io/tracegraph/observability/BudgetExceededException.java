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

    public BudgetExceededException(String nodeName, String tokenType, long spent, long budget) {
        super(String.format("Token budget exceeded at node '%s': %s tokens %d of %d",
                nodeName, tokenType, spent, budget));
        this.spent = (double) spent;
        this.budget = (double) budget;
    }

    public double spent() { return spent; }
    public double budget() { return budget; }
}
