package io.tracegraph.eval;

public record MetricScore(String metricName, boolean passed, double score, String detail) {

    public static MetricScore pass(String metricName, double score) {
        return new MetricScore(metricName, true, score, null);
    }

    public static MetricScore fail(String metricName, double score, String detail) {
        return new MetricScore(metricName, false, score, detail);
    }

    public static MetricScore skipped(String metricName) {
        return new MetricScore(metricName, true, Double.NaN, "skipped");
    }
}
