package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("removal")
class MockEmbeddingClientTest {

    @Test
    void zeroVectorReturnsCorrectDimensions() {
        MockEmbeddingClient client = MockEmbeddingClient.zeros(4);
        List<float[]> result = client.embed(List.of("hello"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(4).containsOnly(0f);
    }

    @Test
    void zeroVectorPreservesInputOrder() {
        MockEmbeddingClient client = MockEmbeddingClient.zeros(2);
        List<float[]> result = client.embed(List.of("a", "b", "c"));

        assertThat(result).hasSize(3);
        for (float[] emb : result) {
            assertThat(emb).hasSize(2);
        }
    }

    @Test
    void customResponderIsApplied() {
        MockEmbeddingClient client = new MockEmbeddingClient(
                texts -> texts.stream().map(t -> new float[]{(float) t.length()}).toList());

        List<float[]> result = client.embed(List.of("ab", "xyz"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)[0]).isEqualTo(2f);
        assertThat(result.get(1)[0]).isEqualTo(3f);
    }

    @Test
    void recordsCallsForAssertion() {
        MockEmbeddingClient client = MockEmbeddingClient.zeros(1);
        client.embed(List.of("first"));
        client.embed(List.of("second", "third"));

        assertThat(client.calls()).hasSize(2);
        assertThat(client.calls().get(0)).containsExactly("first");
        assertThat(client.calls().get(1)).containsExactly("second", "third");
    }

    @Test
    void callsListIsImmutable() {
        MockEmbeddingClient client = MockEmbeddingClient.zeros(1);
        client.embed(List.of("x"));

        assertThatThrownBy(() -> client.calls().add(List.of("y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyInputReturnsEmptyList() {
        MockEmbeddingClient client = MockEmbeddingClient.zeros(3);
        assertThat(client.embed(List.of())).isEmpty();
    }
}
