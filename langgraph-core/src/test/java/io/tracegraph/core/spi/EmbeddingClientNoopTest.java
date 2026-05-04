package io.tracegraph.core.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingClientNoopTest {

    private final EmbeddingClient noop = EmbeddingClient.noop();

    /**
     * The noop client returns an empty list regardless of input — callers that treat a missing
     * result as "no embedding available" need no special-casing for the noop path.
     */
    @Test
    void embedReturnsEmptyList() {
        List<float[]> result = noop.embed(List.of("hello"));
        assertThat(result).isEmpty();
    }
}
