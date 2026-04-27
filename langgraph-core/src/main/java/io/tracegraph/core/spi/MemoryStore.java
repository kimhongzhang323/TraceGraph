package io.tracegraph.core.spi;

import io.tracegraph.core.exec.NoopMemoryStoreAccess;

import java.util.Optional;
import java.util.Set;

public interface MemoryStore {

    Optional<Object> get(String scope, String key);

    void put(String scope, String key, Object value);

    boolean delete(String scope, String key);

    Set<String> keys(String scope);

    static MemoryStore noop() {
        return NoopMemoryStoreAccess.instance();
    }
}
