package io.tracegraph.a2a.http;

/**
 * Thrown when an A2A HTTP request receives a non-2xx response.
 */
public final class A2AHttpException extends RuntimeException {

    private final int statusCode;

    public A2AHttpException(int statusCode, String body) {
        super("A2A HTTP error " + statusCode + ": " + body);
        this.statusCode = statusCode;
    }

    public A2AHttpException(int statusCode, String body, Throwable cause) {
        super("A2A HTTP error " + statusCode + ": " + body, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
