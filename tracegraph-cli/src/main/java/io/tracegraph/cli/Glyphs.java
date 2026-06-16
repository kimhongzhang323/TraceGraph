package io.tracegraph.cli;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The CLI's symbol vocabulary, with a UTF-8 set and an ASCII fallback.
 *
 * <p>Legacy terminals — chiefly Windows consoles on cp1252/cp437 — render Unicode
 * box-drawing and symbol glyphs as {@code ?}. When the output charset isn't UTF-8 the
 * CLI swaps to {@link #ASCII} so the layout stays legible on every OS.
 */
record Glyphs(String marker, String rule, String retry, String cross,
              String bullet, String arrow, String dot,
              String up, String down, String enter, String dash, String ellipsis) {

    static final Glyphs UNICODE =
            new Glyphs("▸", "─", "↻", "✕", "·", "→", "●", "↑", "↓", "⏎", "—", "…");
    static final Glyphs ASCII =
            new Glyphs(">", "-", "x", "x", "-", "->", "*", "up", "dn", "enter", "-", "...");

    /** The active set; set once at startup from the terminal's output charset. */
    static Glyphs active = UNICODE;

    static Glyphs forCharset(Charset charset) {
        return StandardCharsets.UTF_8.equals(charset) ? UNICODE : ASCII;
    }
}
