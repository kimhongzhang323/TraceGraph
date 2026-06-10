package io.tracegraph.memory;

import io.tracegraph.core.spi.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

abstract class MemoryStoreContractTest {

    protected abstract MemoryStore createStore();

    private MemoryStore store;

    @BeforeEach
    void createStoreUnderTest() {
        store = createStore();
    }

    @Test
    void putThenGetRoundTrips() {
        store.put("scope-a", "k1", "v1");
        assertThat(store.get("scope-a", "k1")).contains("v1");
    }

    @Test
    void getReturnsEmptyForMissingKey() {
        assertThat(store.get("scope-a", "missing")).isEmpty();
    }

    @Test
    void deleteRemovesKey() {
        store.put("scope-a", "k1", "v1");
        assertThat(store.delete("scope-a", "k1")).isTrue();
        assertThat(store.get("scope-a", "k1")).isEmpty();
    }

    @Test
    void deleteOfMissingKeyIsNoOp() {
        assertThat(store.delete("scope-a", "missing")).isFalse();
    }

    @Test
    void keysListsOnlyRequestedScope() {
        store.put("scope-a", "k1", "v1");
        store.put("scope-a", "k2", "v2");
        store.put("scope-b", "k3", "v3");
        assertThat(store.keys("scope-a")).containsExactlyInAnyOrder("k1", "k2");
    }

    @Test
    void scopesAreIsolated() {
        store.put("scope-a", "k1", "from-a");
        store.put("scope-b", "k1", "from-b");
        assertThat(store.get("scope-a", "k1")).contains("from-a");
        assertThat(store.get("scope-b", "k1")).contains("from-b");
        store.delete("scope-a", "k1");
        assertThat(store.get("scope-b", "k1")).contains("from-b");
    }

    @Test
    void overwriteReplacesValue() {
        store.put("scope-a", "k1", "old");
        store.put("scope-a", "k1", "new");
        assertThat(store.get("scope-a", "k1")).contains("new");
    }
}
