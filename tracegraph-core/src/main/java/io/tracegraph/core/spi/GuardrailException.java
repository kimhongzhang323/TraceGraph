package io.tracegraph.core.spi;

/**
 * Thrown when a {@link Guardrail} returns a {@link GuardrailVerdict} with action {@code BLOCK}.
 */
public final class GuardrailException extends RuntimeException {

    private final GuardrailVerdict verdict;

    public GuardrailException(String message, GuardrailVerdict verdict) {
        super(message);
        this.verdict = verdict;
    }

    public GuardrailException(String message, GuardrailVerdict verdict, Throwable cause) {
        super(message, cause);
        this.verdict = verdict;
    }

    public GuardrailVerdict verdict() { return verdict; }
}
