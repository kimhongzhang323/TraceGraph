package io.tracegraph.rag;

import io.tracegraph.core.spi.EmbeddingClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double {@link EmbeddingClient} that returns zero vectors of a fixed dimension.
 * Stateless after construction; thread-safe.
 */
public final class MockEmbeddingClient implements EmbeddingClient {

    private final int dimensions;

    public MockEmbeddingClient(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            result.add(new float[dimensions]);
        }
        return result;
    }
}
