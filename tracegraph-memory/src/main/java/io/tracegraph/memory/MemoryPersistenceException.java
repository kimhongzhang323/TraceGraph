package io.tracegraph.memory;

public class MemoryPersistenceException extends RuntimeException {

    public MemoryPersistenceException(String message) {
        super(message);
    }

    public MemoryPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
