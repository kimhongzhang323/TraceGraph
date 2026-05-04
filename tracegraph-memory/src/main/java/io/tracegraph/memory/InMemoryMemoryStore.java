package io.tracegraph.memory;

import io.tracegraph.core.spi.MemoryStore;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryMemoryStore implements MemoryStore {

    private final ConcurrentMap<String, ConcurrentMap<String, Object>> scopes = new ConcurrentHashMap<>();

    @Override
    public Optional<Object> get(String scope, String key) {
        ConcurrentMap<String, Object> entries = scopes.get(scope);
        return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(key));
    }

    @Override
    public void put(String scope, String key, Object value) {
        scopes.computeIfAbsent(scope, s -> new ConcurrentHashMap<>()).put(key, value);
    }

    @Override
    public boolean delete(String scope, String key) {
        ConcurrentMap<String, Object> entries = scopes.get(scope);
        return entries != null && entries.remove(key) != null;
    }

    @Override
    public Set<String> keys(String scope) {
        ConcurrentMap<String, Object> entries = scopes.get(scope);
        return entries == null ? Set.of() : Set.copyOf(entries.keySet());
    }

    public int size(String scope) {
        ConcurrentMap<String, Object> entries = scopes.get(scope);
        return entries == null ? 0 : entries.size();
    }
}
