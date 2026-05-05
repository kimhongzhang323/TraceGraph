package io.tracegraph.eval;

import java.util.List;

public record EvalResult<S>(EvalCase<S> evalCase, S actualOutput, List<MetricScore> scores,
                             long latencyMs, boolean passed) {

    public EvalResult {
        scores = List.copyOf(scores);
    }
}
