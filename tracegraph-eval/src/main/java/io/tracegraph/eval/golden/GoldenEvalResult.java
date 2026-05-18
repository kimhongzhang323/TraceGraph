package io.tracegraph.eval.golden;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;

import java.util.List;

/**
 * Outcome of running one {@link GoldenTrace} through an {@link EvalRunner}.
 *
 * <p>{@code actual} and {@code diff} are populated only when the graph was wired with a
 * {@code RecordingTraceRecorder} and the run succeeded. On execution error, {@code error} is set,
 * {@code diff} is null, and {@code passed} is false.
 *
 * <p>{@code assertionFailures} carries the messages returned by any {@link TraceAssertion}s
 * registered on the runner that rejected a captured step. A non-empty list flips {@code passed}
 * to {@code false} even if the final state and status reproduce the golden's.
 *
 * @param <S>                the graph's state type
 * @param golden             the reference trace used as the baseline
 * @param actual             the trace captured during this evaluation run; null on error or no recorder
 * @param diff               step-by-step comparison of golden vs actual; null when actual is unavailable
 * @param passed             true when the execution reproduced the golden's final state, status,
 *                           and all registered assertions held
 * @param error              the exception thrown by the graph, or null on success
 * @param assertionFailures  per-step assertion failures captured during this run; never null
 */
public record GoldenEvalResult<S>(
        GoldenTrace<S> golden,
        ExecutionTrace<S> actual,
        TraceDiff<S> diff,
        boolean passed,
        Throwable error,
        List<AssertionFailure> assertionFailures) {

    public GoldenEvalResult {
        assertionFailures = assertionFailures == null ? List.of() : List.copyOf(assertionFailures);
    }

    public GoldenEvalResult(GoldenTrace<S> golden,
                            ExecutionTrace<S> actual,
                            TraceDiff<S> diff,
                            boolean passed,
                            Throwable error) {
        this(golden, actual, diff, passed, error, List.of());
    }
}
