package io.tracegraph.eval.metric;

import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.MetricScore;

@FunctionalInterface
public interface Metric<S> {

    MetricScore score(EvalCase<S> evalCase, S actual, long latencyMs);

    /**
     * Whether this metric is applicable to the given case. Returns {@code true} by default.
     * Implementations can opt out of grading specific cases (e.g. an LLM judge metric returns
     * {@code false} when {@link EvalCase#expected()} is {@code null}), in which case
     * {@code EvalSuite} emits a {@link MetricScore#skipped(String)} entry instead of calling
     * {@link #score}.
     */
    default boolean canScore(EvalCase<S> evalCase) {
        return true;
    }

    /**
     * Name reported in skipped {@link MetricScore} entries. Defaults to the implementation's
     * simple class name; concrete metrics that emit a different name from {@link #score} should
     * override this so reports stay consistent between scored and skipped rows.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
