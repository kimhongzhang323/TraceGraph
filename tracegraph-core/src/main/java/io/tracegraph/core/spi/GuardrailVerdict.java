package io.tracegraph.core.spi;

/**
 * The outcome of a {@link Guardrail} check. Immutable value carrier.
 *
 * <p>Three possible actions: {@code ALLOW} (pass through), {@code BLOCK} (reject),
 * {@code TRANSFORM} (signal that the input was modified — callers decide how to act).
 */
public record GuardrailVerdict(Action action, String reason) {

    public enum Action { ALLOW, BLOCK, TRANSFORM }

    public static GuardrailVerdict allow() {
        return new GuardrailVerdict(Action.ALLOW, null);
    }

    public static GuardrailVerdict block(String reason) {
        return new GuardrailVerdict(Action.BLOCK, reason);
    }

    public static GuardrailVerdict transform(String reason) {
        return new GuardrailVerdict(Action.TRANSFORM, reason);
    }

    public boolean isAllowed() { return action == Action.ALLOW; }

    public boolean isBlocked() { return action == Action.BLOCK; }

    public boolean isTransformed() { return action == Action.TRANSFORM; }
}
