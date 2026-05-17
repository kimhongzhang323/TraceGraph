package io.tracegraph.core.spi;

import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreTest {

    @Test
    void contextExposesConfiguredVectorStore() {
        CapturingVectorStore store = new CapturingVectorStore();
        AtomicReference<VectorStore> seen = new AtomicReference<>();

        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> {
                    seen.set(ctx.vectorStore());
                    ctx.vectorStore().upsert("session", "id1", new float[]{1.0f, 0.0f}, Map.of());
                    return s;
                })
                .entry("n").terminal("n")
                .vectorStore(store)
                .build();

        g.run("seed");

        assertThat(seen.get()).isSameAs(store);
        assertThat(store.upserted).hasSize(1);
        assertThat(store.upserted.get(0)).isEqualTo("session:id1");
    }

    @Test
    void defaultContextVectorStoreIsNoop() {
        AtomicReference<List<VectorStore.VectorMatch>> result = new AtomicReference<>();

        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> {
                    ctx.vectorStore().upsert("scope", "k", new float[]{1.0f}, Map.of());
                    result.set(ctx.vectorStore().query("scope", new float[]{1.0f}, 5));
                    return s;
                })
                .entry("n").terminal("n")
                .build();

        g.run("seed");

        assertThat(result.get()).isEmpty();
    }

    @Test
    void noopStaticFactoryDiscardsAllOperations() {
        VectorStore noop = VectorStore.noop();
        noop.upsert("s", "id", new float[]{1f}, Map.of("k", "v"));
        assertThat(noop.query("s", new float[]{1f}, 10)).isEmpty();
        noop.delete("s", "id");
        assertThat(noop.query("s", new float[]{1f}, 10)).isEmpty();
    }

    private static final class CapturingVectorStore implements VectorStore {
        final List<String> upserted = new CopyOnWriteArrayList<>();

        @Override
        public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
            upserted.add(scope + ":" + id);
        }

        @Override
        public List<VectorMatch> query(String scope, float[] embedding, int topK) {
            return List.of();
        }

        @Override
        public void delete(String scope, String id) {}
    }
}
