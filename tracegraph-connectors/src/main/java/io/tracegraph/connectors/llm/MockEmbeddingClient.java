package io.tracegraph.connectors.llm;

import io.tracegraph.core.spi.EmbeddingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Test-double {@link EmbeddingClient} backed by a configurable responder function. Thread-safe.
 *
 * <p>Use the static factories for common cases:
 * <ul>
 *   <li>{@link #zeros(int)} — returns zero vectors of fixed dimension, useful for most unit tests.
 * </ul>
 * Pass a lambda to the constructor when you need call-dependent behaviour.
 *
 * <p>Recorded calls are available via {@link #calls()} for post-hoc assertion.
 *
 * @deprecated duplicate of {@code io.tracegraph.rag.MockEmbeddingClient}, which is the canonical
 * implementation (used by the Spring Boot starter and examples). Will be removed in 0.5.
 */
@Deprecated(since = "0.4", forRemoval = true)
public final class MockEmbeddingClient implements EmbeddingClient {

    private final Function<List<String>, List<float[]>> responder;
    private final List<List<String>> calls = new CopyOnWriteArrayList<>();

    public MockEmbeddingClient(Function<List<String>, List<float[]>> responder) {
        this.responder = Objects.requireNonNull(responder, "responder");
    }

    /** Returns zero vectors of {@code dimensions} length for every text. */
    public static MockEmbeddingClient zeros(int dimensions) {
        return new MockEmbeddingClient(texts -> {
            List<float[]> result = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                result.add(new float[dimensions]);
            }
            return result;
        });
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        calls.add(List.copyOf(texts));
        return responder.apply(texts);
    }

    /** Returns a snapshot of all input batches passed to {@link #embed} so far. */
    public List<List<String>> calls() {
        return List.copyOf(calls);
    }
}
