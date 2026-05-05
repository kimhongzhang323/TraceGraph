package io.tracegraph.connectors.structured;

public final class StructuredOutputException extends RuntimeException {

    public StructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }

    public StructuredOutputException(String message) {
        super(message);
    }
}
