package io.tracegraph.eval.golden;

import io.tracegraph.observability.replay.TraceStep;

import java.util.Optional;

/**
 * Per-step assertion run against the captured {@link TraceStep}s of a re-executed
 * {@link io.tracegraph.observability.replay.ExecutionTrace} during a golden eval.
 *
 * <p>Implementations return {@link Optional#empty()} when the step satisfies the contract, or a
 * {@link Optional} containing a human-readable failure message when it doesn't. Returned messages
 * become {@link AssertionFailure} entries on {@link GoldenEvalResult#assertionFailures()} and
 * flip {@link GoldenEvalResult#passed()} to {@code false}.
 *
 * <p>Bound to a node name via {@link EvalRunner.Builder#assertion(String, TraceAssertion)} —
 * each assertion runs once per top-level trace step whose {@code nodeName} matches.
 *
 * <p>Implementations must be thread-safe if reused across executions (suite-level wiring).
 *
 * @param <S> the graph's state type
 */
@FunctionalInterface
public interface TraceAssertion<S> {

    /**
     * Inspect {@code step} and return a failure message, or {@link Optional#empty()} on success.
     *
     * @param step the trace step to inspect; never {@code null}
     */
    Optional<String> check(TraceStep<S> step);
}
