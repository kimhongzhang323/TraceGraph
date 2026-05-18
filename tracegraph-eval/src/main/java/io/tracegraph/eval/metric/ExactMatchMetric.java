package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

import java.util.Objects;

public final class ExactMatchMetric<S> implements Metric<S> {

    private static final String NAME = "exact_match";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        if (Objects.equals(evalCase.expected(), actual)) {
            return MetricScore.pass(NAME, 1.0);
        }
        return MetricScore.fail(NAME, 0.0,
                "expected=" + evalCase.expected() + " actual=" + actual);
    }
}
