package io.tracegraph.runtime;

public class CheckpointPersistenceException extends RuntimeException {

    public CheckpointPersistenceException(String message) {
        super(message);
    }

    public CheckpointPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
