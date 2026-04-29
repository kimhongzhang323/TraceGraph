package io.tracegraph.core.spi;

import io.tracegraph.core.exec.NoopMemoryStoreAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cross-execution key-value store keyed by an explicit {@code scope} per call. Working memory is
 * the state object itself; this SPI is for data that must outlive a single execution (session
 * memory, long-term facts, semantic recall).
 *
 * <p>Wired into a graph via {@code Graph.Builder.memoryStore(...)}; nodes access it through
 * {@code ctx.memory()}. Implementations must be thread-safe. The default {@link #noop()} discards
 * writes and returns empty reads.
 */
public interface MemoryStore {

    Optional<Object> get(String scope, String key);

    void put(String scope, String key, Object value);

    boolean delete(String scope, String key);

    Set<String> keys(String scope);

    default List<String> pagedKeys(String scope, int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        List<String> sorted = new ArrayList<>(new TreeSet<>(keys(scope)));
        int from = Math.min(offset, sorted.size());
        int to = Math.min(from + limit, sorted.size());
        return List.copyOf(sorted.subList(from, to));
    }

    static MemoryStore noop() {
        return NoopMemoryStoreAccess.instance();
    }
}
