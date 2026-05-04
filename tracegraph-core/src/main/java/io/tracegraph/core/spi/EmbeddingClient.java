package io.tracegraph.core.spi;

import java.util.List;

/**
 * Vendor-neutral embedding SPI. Converts a batch of texts into dense float vectors.
 *
 * <p>Implemented by vendor adapters in {@code langgraph-connectors}. The SAM design allows
 * lambda assignment for tests and simple inline adapters. The default {@link #noop()} always
 * returns an empty list — useful when embedding is optional in a graph.
 */
@FunctionalInterface
public interface EmbeddingClient {

    /**
     * Embed a batch of texts. The returned list preserves input order: {@code result.get(i)}
     * corresponds to {@code texts.get(i)}.
     *
     * @param texts non-null list of texts to embed
     * @return parallel list of embedding vectors; never {@code null}
     */
    List<float[]> embed(List<String> texts);

    /**
     * Returns a no-op {@link EmbeddingClient} that always returns an empty list regardless of
     * input. Useful as a default when no real embedding provider is configured.
     */
    static EmbeddingClient noop() {
        return texts -> List.of();
    }
}
