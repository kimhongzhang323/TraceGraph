package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

@FunctionalInterface
public interface Metric<S> {
    MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs);
}
