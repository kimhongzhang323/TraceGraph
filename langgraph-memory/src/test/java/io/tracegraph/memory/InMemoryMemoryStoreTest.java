package io.tracegraph.memory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMemoryStoreTest {

    @Test
    void putAndGetRoundTrip() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.put("session:abc", "user", "alice");

        assertThat(store.get("session:abc", "user")).contains("alice");
        assertThat(store.get("session:abc", "missing")).isEmpty();
        assertThat(store.get("other-scope", "user")).isEmpty();
    }

    @Test
    void deleteRemovesEntryAndReportsExistence() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.put("s", "k", 42);

        assertThat(store.delete("s", "k")).isTrue();
        assertThat(store.delete("s", "k")).isFalse();
        assertThat(store.get("s", "k")).isEmpty();
    }

    @Test
    void keysListsScopedEntries() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.put("s1", "a", 1);
        store.put("s1", "b", 2);
        store.put("s2", "c", 3);

        assertThat(store.keys("s1")).containsExactlyInAnyOrder("a", "b");
        assertThat(store.keys("s2")).containsExactly("c");
        assertThat(store.keys("missing")).isEmpty();
    }

    @Test
    void scopesAreIsolated() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.put("session:a", "key", "A");
        store.put("session:b", "key", "B");

        assertThat(store.get("session:a", "key")).contains("A");
        assertThat(store.get("session:b", "key")).contains("B");
    }

    @Test
    void concurrentPutsKeepAllValues() throws Exception {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        int n = 100;
        CountDownLatch done = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            int idx = i;
            Thread.ofVirtual().start(() -> {
                try { store.put("scope", "key-" + idx, idx); }
                finally { done.countDown(); }
            });
        }
        done.await();

        assertThat(store.size("scope")).isEqualTo(n);
        for (int i = 0; i < n; i++) {
            assertThat(store.get("scope", "key-" + i)).contains(i);
        }
    }

    @Test
    void pagedKeysReturnsSortedSliceWithDefaultImplementation() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.put("s", "delta", 1);
        store.put("s", "alpha", 1);
        store.put("s", "charlie", 1);
        store.put("s", "bravo", 1);

        assertThat(store.pagedKeys("s", 0, 2)).containsExactly("alpha", "bravo");
        assertThat(store.pagedKeys("s", 2, 2)).containsExactly("charlie", "delta");
        assertThat(store.pagedKeys("s", 4, 10)).isEmpty();
    }

    @Test
    void pagedKeysRejectsNegativeArgs() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        assertThatThrownBy(() -> store.pagedKeys("s", -1, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.pagedKeys("s", 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
