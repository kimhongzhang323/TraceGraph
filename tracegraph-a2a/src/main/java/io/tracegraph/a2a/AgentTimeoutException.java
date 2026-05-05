package io.tracegraph.a2a;

/**
 * Thrown when {@link AgentBus#sendAndAwait} does not receive a reply within the specified timeout.
 */
public final class AgentTimeoutException extends RuntimeException {

    public AgentTimeoutException(String message) {
        super(message);
    }

    public AgentTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
