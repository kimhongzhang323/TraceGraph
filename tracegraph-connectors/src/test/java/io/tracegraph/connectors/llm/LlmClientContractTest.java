package io.tracegraph.connectors.llm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Provider contract test suite.
 *
 * <p>Each contract behavior is exercised against every {@link LlmClient} provider via cassette
 * replay — no real API keys or network access required in CI. Cassette files live under
 * {@code src/test/resources/cassettes/}.
 */
class LlmClientContractTest {

    // A minimal valid request used in contract tests.
    private static final LlmRequest HELLO_REQUEST = new LlmRequest(
            List.of(ChatMessage.user("Hello")),
            "test-model",
            0.7,
            100
    );

    static Stream<LlmClient> clients() {
        return Stream.of(
                CassetteLlmClient.replayingFromClasspath("cassettes/openai.json"),
                CassetteLlmClient.replayingFromClasspath("cassettes/anthropic.json"),
                CassetteLlmClient.replayingFromClasspath("cassettes/gemini.json"),
                CassetteLlmClient.replayingFromClasspath("cassettes/ollama.json"),
                CassetteLlmClient.replayingFromClasspath("cassettes/mock.json")
        );
    }

    @ParameterizedTest
    @MethodSource("clients")
    void completeShouldReturnNonNullResponse(LlmClient client) {
        LlmResponse response = client.complete(HELLO_REQUEST);
        assertThat(response).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("clients")
    void contentShouldBeNonNullAndNonEmpty(LlmClient client) {
        LlmResponse response = client.complete(HELLO_REQUEST);
        assertThat(response.content()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("clients")
    void finishReasonShouldBeNonNull(LlmClient client) {
        LlmResponse response = client.complete(HELLO_REQUEST);
        assertThat(response.finish()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("clients")
    void usageTokensShouldBeNonNegative(LlmClient client) {
        LlmResponse response = client.complete(HELLO_REQUEST);
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().promptTokens()).isGreaterThanOrEqualTo(0);
        assertThat(response.usage().completionTokens()).isGreaterThanOrEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("clients")
    void toolCallsShouldBeNonNull(LlmClient client) {
        LlmResponse response = client.complete(HELLO_REQUEST);
        assertThat(response.toolCalls()).isNotNull();
    }

    // ---- CassetteLlmClient-specific contract tests ----

    @ParameterizedTest
    @MethodSource("clients")
    void exhaustedCassetteShouldThrowCassetteExhaustedException(LlmClient client) {
        // Drain the one recorded response
        client.complete(HELLO_REQUEST);
        // Next call should throw
        assertThatCode(() -> client.complete(HELLO_REQUEST))
                .isInstanceOf(CassetteLlmClient.CassetteExhaustedException.class);
    }
}
