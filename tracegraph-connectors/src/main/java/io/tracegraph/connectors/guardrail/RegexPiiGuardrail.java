package io.tracegraph.connectors.guardrail;

import io.tracegraph.core.spi.Guardrail;
import io.tracegraph.core.spi.GuardrailVerdict;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocks input that matches any of the supplied PII patterns.
 *
 * <p>Use {@link #defaults()} for a reasonable set covering email, SSN, credit card, and phone.
 */
public final class RegexPiiGuardrail implements Guardrail<String> {

    private static final List<Pattern> DEFAULT_PATTERNS = List.of(
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12})\\b"),
            Pattern.compile("\\b(?:\\+?1[-.\\s]?)?\\(?[0-9]{3}\\)?[-.\\s]?[0-9]{3}[-.\\s]?[0-9]{4}\\b")
    );

    private final List<Pattern> patterns;

    public static RegexPiiGuardrail defaults() {
        return new RegexPiiGuardrail(DEFAULT_PATTERNS);
    }

    public RegexPiiGuardrail(List<Pattern> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    @Override
    public GuardrailVerdict check(String input) {
        if (input == null) {
            return GuardrailVerdict.allow();
        }
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(input);
            if (m.find()) {
                return GuardrailVerdict.block("Input matches PII pattern: " + pattern.pattern());
            }
        }
        return GuardrailVerdict.allow();
    }
}
