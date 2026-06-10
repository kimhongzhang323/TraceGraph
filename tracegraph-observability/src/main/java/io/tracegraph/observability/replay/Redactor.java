package io.tracegraph.observability.replay;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Scrubs sensitive substrings from text before it is persisted or served.
 *
 * <p>Applied by {@link RedactingTraceStore} to raw LLM I/O and error messages. Implementations
 * must be thread-safe and must tolerate {@code null} input (return {@code null}).
 */
@FunctionalInterface
public interface Redactor {

    String redact(String text);

    /** Pass-through redactor. */
    Redactor NONE = text -> text;

    /**
     * Pattern-based redactor covering common credential shapes: provider API keys
     * (OpenAI/Anthropic {@code sk-*}), AWS access key IDs, bearer tokens, JWTs, and
     * {@code api_key=...}-style assignments. Matches are replaced with {@code [REDACTED]}.
     *
     * <p>This is a safety net, not a guarantee — compose with domain-specific patterns via
     * {@link #andThen(Redactor)} for anything beyond generic credentials.
     */
    static Redactor defaultPatterns() {
        List<Pattern> patterns = List.of(
                Pattern.compile("sk-[A-Za-z0-9_-]{16,}"),
                Pattern.compile("AKIA[0-9A-Z]{16}"),
                Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{16,}"),
                Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
                Pattern.compile("(?i)(api[_-]?key|secret|password|token)\\s*[:=]\\s*\\S{8,}"));
        return text -> {
            if (text == null) return null;
            String out = text;
            for (Pattern p : patterns) {
                out = p.matcher(out).replaceAll("[REDACTED]");
            }
            return out;
        };
    }

    default Redactor andThen(Redactor next) {
        return text -> next.redact(this.redact(text));
    }
}
