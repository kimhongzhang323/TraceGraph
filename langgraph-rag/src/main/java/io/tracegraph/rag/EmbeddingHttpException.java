package io.tracegraph.rag;

/**
 * Thrown by {@link OpenAiEmbeddingClient} when the HTTP response status is not 2xx.
 */
public final class EmbeddingHttpException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public EmbeddingHttpException(int statusCode, String body) {
        super("Embedding HTTP error " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

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
