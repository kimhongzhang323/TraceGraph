package io.tracegraph.eval.metric;

import java.util.List;

final class TextTokens {

    private TextTokens() {}

    static List<String> tokenize(String text) {
        if (text == null) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return List.of(trimmed.split("\\s+"));
    }
}
