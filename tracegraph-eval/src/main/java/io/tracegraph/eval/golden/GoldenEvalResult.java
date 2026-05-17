package io.tracegraph.eval.golden;

import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;

/**
 * Outcome of running one {@link GoldenTrace} through an {@link EvalRunner}.
 *
 * <p>{@code actual} and {@code diff} are populated only when the graph was wired with a
 * {@code RecordingTraceRecorder} and the run succeeded. On execution error, {@code error} is set,
 * {@code diff} is null, and {@code passed} is false.
 *
 * @param <S>    the graph's state type
 * @param golden the reference trace used as the baseline
 * @param actual the trace captured during this evaluation run; null on error or no recorder
 * @param diff   step-by-step comparison of golden vs actual; null when actual is unavailable
 * @param passed true when the execution reproduced the golden's final state and status
 * @param error  the exception thrown by the graph, or null on success
 */
public record GoldenEvalResult<S>(
        GoldenTrace<S> golden,
        ExecutionTrace<S> actual,
        TraceDiff<S> diff,
        boolean passed,
        Throwable error) {
}
