package io.tracegraph.observability;

@FunctionalInterface
public interface StateRenderer {

    StateRenderer DEFAULT = String::valueOf;

    String render(Object state);

    /** Caps {@link #DEFAULT} output at {@code maxChars}; large states get a truncation marker. */
    static StateRenderer capped(int maxChars) {
        return capped(DEFAULT, maxChars);
    }

    static StateRenderer capped(StateRenderer delegate, int maxChars) {
        if (maxChars <= 0) throw new IllegalArgumentException("maxChars must be > 0");
        return state -> {
            String rendered = delegate.render(state);
            if (rendered == null || rendered.length() <= maxChars) return rendered;
            return rendered.substring(0, maxChars)
                    + "...[truncated " + (rendered.length() - maxChars) + " chars]";
        };
    }
}
