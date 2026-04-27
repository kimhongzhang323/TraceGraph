package io.tracegraph.core.exec;

import io.tracegraph.core.spi.MemoryStore;

import java.util.Optional;
import java.util.Set;

public final class NoopMemoryStoreAccess {

    private static final MemoryStore INSTANCE = new MemoryStore() {
        @Override public Optional<Object> get(String scope, String key) { return Optional.empty(); }
        @Override public void put(String scope, String key, Object value) {}
        @Override public boolean delete(String scope, String key) { return false; }
        @Override public Set<String> keys(String scope) { return Set.of(); }
    };

    private NoopMemoryStoreAccess() {}

    public static MemoryStore instance() {
        return INSTANCE;
    }
}
