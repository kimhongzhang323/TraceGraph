package io.tracegraph.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlyphsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void utf8TerminalsKeepUnicodeGlyphs() {
        assertThat(Glyphs.forCharset(StandardCharsets.UTF_8)).isSameAs(Glyphs.UNICODE);
    }

    @Test
    void legacyCharsetsFallBackToAscii() {
        assertThat(Glyphs.forCharset(StandardCharsets.ISO_8859_1)).isSameAs(Glyphs.ASCII);
        assertThat(Glyphs.forCharset(Charset.forName("windows-1252"))).isSameAs(Glyphs.ASCII);
    }

    @Test
    void asciiModeRendersWithoutNonAsciiCharacters() throws Exception {
        Glyphs previous = Glyphs.active;
        Glyphs.active = Glyphs.ASCII;
        try {
            var trace = MAPPER.readTree("""
                    {"status":"FAILED","startedAt":"2026-06-12T01:00:00Z","steps":[
                      {"nodeName":"charge","attempts":3,"error":{"message":"declined"}}
                    ]}""");
            String plain = String.join("\n",
                    Screens.traceDetail("exec-a", trace, 0, true, 100).stream().map(Ansi::strip).toList());

            assertThat(plain).isNotEmpty();
            assertThat(plain.chars().allMatch(c -> c < 128)).isTrue();
            assertThat(plain).contains(">").contains("x3");
        } finally {
            Glyphs.active = previous;
        }
    }
}
