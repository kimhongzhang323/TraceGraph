package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

public final class LatencyMetric<S> implements Metric<S> {

    private static final String NAME = "latency";

    private final long maxMillis;

    public LatencyMetric(long maxMillis) {
        if (maxMillis <= 0) {
            throw new IllegalArgumentException("maxMillis must be > 0");
        }
        this.maxMillis = maxMillis;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        double rawScore = 1.0 - (latencyMs / (double) maxMillis);
        double score = Math.max(0.0, Math.min(1.0, rawScore));
        if (latencyMs <= maxMillis) {
            return MetricScore.pass(NAME, score);
        }
        return MetricScore.fail(NAME, 0.0,
                "latency " + latencyMs + "ms exceeded max " + maxMillis + "ms");
    }
}
