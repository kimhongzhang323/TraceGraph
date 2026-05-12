package io.tracegraph.rag;

/**
 * Thrown by vector store clients when the HTTP response status is not 2xx, or when a
 * network/parsing error occurs ({@code statusCode == -1}).
 */
public final class VectorStoreHttpException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public VectorStoreHttpException(int statusCode, String body) {
        super("VectorStore HTTP error " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

    public VectorStoreHttpException(String message) {
        super(message);
        this.statusCode = -1;
        this.body = "";
    }

    public VectorStoreHttpException(String message, Throwable cause) {
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
