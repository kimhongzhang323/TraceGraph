package io.tracegraph.rag;

/**
 * Thrown by {@link PgVectorStore} when a database operation fails.
 */
public final class PgVectorException extends RuntimeException {

    public PgVectorException(String message) {
        super(message);
    }

    public PgVectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
