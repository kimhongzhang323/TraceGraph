package io.tracegraph.connectors.guardrail;

import io.tracegraph.core.spi.Guardrail;
import io.tracegraph.core.spi.GuardrailVerdict;

/**
 * Blocks input whose character length exceeds {@code maxChars}.
 */
public final class LengthGuardrail implements Guardrail<String> {

    private final int maxChars;

    public LengthGuardrail(int maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public GuardrailVerdict check(String input) {
        if (input != null && input.length() > maxChars) {
            return GuardrailVerdict.block(
                    "Input length " + input.length() + " exceeds max " + maxChars);
        }
        return GuardrailVerdict.allow();
    }
}
