package io.tracegraph.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmbeddingClientTest {

    @Test
    void embedReturnsCorrectDimensions() {
        MockEmbeddingClient client = new MockEmbeddingClient(5);
        List<float[]> result = client.embed(List.of("hello"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(5);
    }

    @Test
    void embedMultipleTexts() {
        MockEmbeddingClient client = new MockEmbeddingClient(3);
        List<float[]> result = client.embed(List.of("a", "b", "c"));

        assertThat(result).hasSize(3);
        for (float[] emb : result) {
            assertThat(emb).hasSize(3);
        }
    }
}
