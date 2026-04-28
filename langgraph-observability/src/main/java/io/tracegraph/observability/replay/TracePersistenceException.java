package io.tracegraph.observability.replay;

public class TracePersistenceException extends RuntimeException {

    public TracePersistenceException(String message) {
        super(message);
    }

    public TracePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
