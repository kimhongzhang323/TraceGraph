package io.tracegraph.core.spi;

/**
 * SPI for validating or gating arbitrary inputs before they reach a node or LLM call.
 *
 * <p>{@code Guardrail<T>} is intentionally generic so {@code tracegraph-core} stays dep-free.
 * Typical usages: {@code Guardrail<String>} for plain text, {@code Guardrail<LlmRequest>} in
 * connectors, {@code Guardrail<LlmResponse>} for output checking.
 *
 * <p>Compose multiple guardrails via {@link #andThen(Guardrail)} — short-circuits on first block.
 */
@FunctionalInterface
public interface Guardrail<T> {

    GuardrailVerdict check(T input);

    /**
     * Returns a composed guardrail that runs {@code next} only when {@code this} allows.
     * If {@code this} blocks, the block verdict is returned immediately.
     */
    default Guardrail<T> andThen(Guardrail<T> next) {
        if (next == null) throw new NullPointerException("next guardrail cannot be null");
        return input -> {
            GuardrailVerdict v = this.check(input);
            return v.isBlocked() ? v : next.check(input);
        };
    }
}
