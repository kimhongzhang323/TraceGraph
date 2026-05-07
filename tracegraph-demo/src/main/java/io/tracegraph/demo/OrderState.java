package io.tracegraph.demo;

public record OrderState(
        String orderId,
        double amount,
        boolean valid,
        double riskScore,
        boolean charged,
        boolean shipped,
        String error
) {
    public static OrderState of(String orderId, double amount) {
        return new OrderState(orderId, amount, false, 0.0, false, false, null);
    }

    public OrderState withValid(boolean v) {
        return new OrderState(orderId, amount, v, riskScore, charged, shipped, error);
    }

    public OrderState withRiskScore(double r) {
        return new OrderState(orderId, amount, valid, r, charged, shipped, error);
    }

    public OrderState withCharged(boolean c) {
        return new OrderState(orderId, amount, valid, riskScore, c, shipped, error);
    }

    public OrderState withShipped(boolean s) {
        return new OrderState(orderId, amount, valid, riskScore, charged, s, error);
    }

    public OrderState withError(String e) {
        return new OrderState(orderId, amount, valid, riskScore, charged, shipped, e);
    }
}
