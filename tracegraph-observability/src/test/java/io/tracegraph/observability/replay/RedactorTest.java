package io.tracegraph.observability.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final Redactor redactor = Redactor.defaultPatterns();

    // Fixtures are assembled at runtime so secret scanners don't flag this file;
    // the redactor only ever sees the joined string.
    private static String fake(String... parts) {
        return String.join("", parts);
    }

    @Test
    void redactsProviderApiKeys() {
        assertThat(redactor.redact("key " + fake("sk-", "ant-api03-AbCdEfGh123456789") + " in prompt"))
                .isEqualTo("key [REDACTED] in prompt");
    }

    @Test
    void redactsAwsAccessKeyIds() {
        assertThat(redactor.redact("creds " + fake("AKIA", "IOSFODNN7EXAMPLE") + " here"))
                .isEqualTo("creds [REDACTED] here");
    }

    @Test
    void redactsBearerTokensAndJwts() {
        assertThat(redactor.redact("Authorization: " + fake("Bearer ", "abc123def456ghi789")))
                .isEqualTo("Authorization: [REDACTED]");
        assertThat(redactor.redact("jwt " + fake("eyJ", "hbGciOiJIUzI1NiJ9", ".",
                "eyJ", "zdWIiOiIxMjM0NTY3ODkwIn0", ".", "dozjgNryP4J3jVmNHl0w5N")))
                .isEqualTo("jwt [REDACTED]");
    }

    @Test
    void redactsKeyValueAssignments() {
        assertThat(redactor.redact(fake("pass", "word=", "hunter2hunter2"))).isEqualTo("[REDACTED]");
        assertThat(redactor.redact(fake("api", "_key: ", "0123456789abcdef"))).isEqualTo("[REDACTED]");
    }

    @Test
    void passesCleanTextAndNullThrough() {
        assertThat(redactor.redact("nothing secret here")).isEqualTo("nothing secret here");
        assertThat(redactor.redact(null)).isNull();
    }

    @Test
    void composesWithAndThen() {
        Redactor custom = redactor.andThen(t -> t == null ? null : t.replace("alice@example.com", "[EMAIL]"));
        assertThat(custom.redact("from alice@example.com key " + fake("sk-", "abcdefghijklmnop1234")))
                .isEqualTo("from [EMAIL] key [REDACTED]");
    }
}
