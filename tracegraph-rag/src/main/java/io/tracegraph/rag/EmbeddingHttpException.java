package io.tracegraph.rag;

/**
 * Thrown by embedding clients when the HTTP response status is not 2xx.
 *
 * <p>When {@code statusCode == -1}, the exception was not created from an HTTP response and
 * instead wraps a network or parsing error (see {@link #EmbeddingHttpException(String)},
 * {@link #EmbeddingHttpException(String, Throwable)}).
 */
public final class EmbeddingHttpException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public EmbeddingHttpException(int statusCode, String body) {
        super("Embedding HTTP error " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * Constructs an exception from a message, typically for network or parsing errors.
     * {@code statusCode} is set to -1 to indicate this did not come from an HTTP response.
     */
    public EmbeddingHttpException(String message) {
        super(message);
        this.statusCode = -1;
        this.body = "";
    }

    /**
     * Constructs an exception from a message and cause, typically for network or parsing errors.
     * {@code statusCode} is set to -1 to indicate this did not come from an HTTP response.
     */
    public EmbeddingHttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.body = "";
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }
}
