package io.tracegraph.core.spi;

import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStoreTest {

    @Test
    void contextExposesConfiguredStore() {
        RecordingStore store = new RecordingStore();
        AtomicReference<MemoryStore> seen = new AtomicReference<>();

        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> {
                    seen.set(ctx.memory());
                    ctx.memory().put("session", "k", "v");
                    return s;
                })
                .entry("n").terminal("n")
                .memoryStore(store)
                .build();

        g.run("seed");

        assertThat(seen.get()).isSameAs(store);
        assertThat(store.get("session", "k")).contains("v");
    }

    @Test
    void defaultContextMemoryIsNoop() {
        AtomicReference<Optional<Object>> result = new AtomicReference<>();

        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> {
                    ctx.memory().put("scope", "k", "ignored");
                    result.set(ctx.memory().get("scope", "k"));
                    return s;
                })
                .entry("n").terminal("n")
                .build();

        g.run("seed");

        assertThat(result.get()).isEmpty();
    }

    private static final class RecordingStore implements MemoryStore {
        private final ConcurrentMap<String, ConcurrentMap<String, Object>> data = new ConcurrentHashMap<>();

        @Override public Optional<Object> get(String scope, String key) {
            var m = data.get(scope);
            return m == null ? Optional.empty() : Optional.ofNullable(m.get(key));
        }
        @Override public void put(String scope, String key, Object value) {
            data.computeIfAbsent(scope, s -> new ConcurrentHashMap<>()).put(key, value);
        }
        @Override public boolean delete(String scope, String key) {
            var m = data.get(scope);
            return m != null && m.remove(key) != null;
        }
        @Override public Set<String> keys(String scope) {
            var m = data.get(scope);
            return m == null ? Set.of() : Set.copyOf(m.keySet());
        }
    }
}
