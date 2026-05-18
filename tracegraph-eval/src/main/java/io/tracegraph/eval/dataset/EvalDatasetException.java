package io.tracegraph.eval.dataset;

/**
 * Thrown when an {@link EvalCaseLoader} cannot parse a dataset file
 * (missing file, malformed JSON / CSV, missing required field).
 */
public class EvalDatasetException extends RuntimeException {

    public EvalDatasetException(String message) {
        super(message);
    }

    public EvalDatasetException(String message, Throwable cause) {
        super(message, cause);
    }
}
