package io.tracegraph.eval.golden;

import java.util.Objects;

/**
 * One failed {@link TraceAssertion} captured during a golden eval. Aggregated in
 * {@link GoldenEvalResult#assertionFailures()}.
 *
 * @param nodeName  name of the node whose trace step was inspected
 * @param stepIndex zero-based index of the offending step within the captured trace
 * @param message   human-readable failure message returned by the assertion
 */
public record AssertionFailure(String nodeName, int stepIndex, String message) {

    public AssertionFailure {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(message, "message");
        if (stepIndex < 0) throw new IllegalArgumentException("stepIndex must be >= 0");
    }
}
