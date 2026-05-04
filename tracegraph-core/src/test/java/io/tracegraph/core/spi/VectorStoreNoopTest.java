package io.tracegraph.core.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreNoopTest {

    private final VectorStore noop = VectorStore.noop();

    @Test
    void upsertDoesNotThrow() {
        noop.upsert("scope", "id1", new float[]{0.1f, 0.2f}, Map.of("k", "v"));
    }

    @Test
    void queryReturnsEmptyList() {
        List<VectorStore.VectorMatch> results =
                noop.query("scope", new float[]{0.1f, 0.2f}, 5);
        assertThat(results).isEmpty();
    }

    @Test
    void deleteDoesNotThrow() {
        noop.delete("scope", "id1");
    }
}
