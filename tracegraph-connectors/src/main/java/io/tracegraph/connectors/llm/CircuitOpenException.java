package io.tracegraph.connectors.llm;

/** Thrown by {@link CircuitBreakerLlmClient} when the circuit is open and calls are rejected fast. */
public final class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String message) {
        super(message);
    }

    public CircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
