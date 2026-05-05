package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

public final class ContainsMetric<S> implements Metric<S> {

    private static final String NAME = "contains";

    @Override
    public MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs) {
        String expectedStr = evalCase.expected() == null ? "" : evalCase.expected().toString();
        String actualStr = actual == null ? "" : actual.toString();
        if (actualStr.contains(expectedStr)) {
            return MetricScore.pass(NAME, 1.0);
        }
        return MetricScore.fail(NAME, 0.0,
                "actual does not contain expected substring: " + expectedStr);
    }
}
