package io.tracegraph.demo;

import io.tracegraph.core.Context;

public final class OrderNodes {

    private OrderNodes() {}

    public static OrderState validate(OrderState s, Context ctx) {
        if (s.amount() <= 0) {
            throw new IllegalArgumentException("invalid order");
        }
        return s.withValid(true);
    }

    public static OrderState enrich(OrderState s, Context ctx) {
        double score = s.amount() > 1000 ? 0.7 : 0.2;
        return s.withRiskScore(score);
    }

    public static OrderState score(OrderState s, Context ctx) {
        ctx.logger().info("scoring order {} riskScore={}", s.orderId(), s.riskScore());
        return s;
    }

    public static OrderState charge(OrderState s, Context ctx) {
        if (s.amount() == 999.0) {
            throw new RuntimeException("payment declined");
        }
        return s.withCharged(true);
    }

    public static OrderState ship(OrderState s, Context ctx) {
        return s.withShipped(true);
    }
}
