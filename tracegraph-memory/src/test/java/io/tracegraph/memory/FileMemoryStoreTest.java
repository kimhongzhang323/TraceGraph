package io.tracegraph.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileMemoryStoreTest {

    public record Profile(String name, int age) {}

    @Test
    void roundTripsPrimitives(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        store.put("session", "count", 42);
        store.put("session", "label", "hello");
        assertThat(store.get("session", "count")).contains(42);
        assertThat(store.get("session", "label")).contains("hello");
    }

    @Test
    void roundTripsRecord(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        Profile p = new Profile("alice", 30);
        store.put("users", "alice", p);
        assertThat(store.get("users", "alice")).contains(p);
    }

    @Test
    void roundTripsCollections(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        store.put("s", "list", List.of(1, 2, 3));
        store.put("s", "map", Map.of("a", 1, "b", 2));
        assertThat(store.get("s", "list")).contains(List.of(1, 2, 3));
        assertThat(store.get("s", "map")).contains(Map.of("a", 1, "b", 2));
    }

    @Test
    void getReturnsEmptyForMissing(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        assertThat(store.get("nope", "nope")).isEmpty();
    }

    @Test
    void deleteReturnsTrueOnceThenFalse(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        store.put("s", "k", "v");
        assertThat(store.delete("s", "k")).isTrue();
        assertThat(store.delete("s", "k")).isFalse();
        assertThat(store.get("s", "k")).isEmpty();
    }

    @Test
    void keysListsCurrentEntries(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        store.put("s", "a", 1);
        store.put("s", "b", 2);
        assertThat(store.keys("s")).containsExactlyInAnyOrder("a", "b");
        assertThat(store.keys("missing")).isEmpty();
    }

    @Test
    void overwriteReplacesValue(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        store.put("s", "k", 1);
        store.put("s", "k", 2);
        assertThat(store.get("s", "k")).contains(2);
    }

    @Test
    void rejectsPathTraversalInScopeOrKey(@TempDir Path tmp) {
        FileMemoryStore store = FileMemoryStore.of(tmp);
        assertThatThrownBy(() -> store.put("../escape", "k", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("s", "a/b", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get("s", "..")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete("s\\x", "k")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void survivesReopen(@TempDir Path tmp) {
        FileMemoryStore first = FileMemoryStore.of(tmp);
        first.put("s", "k", new Profile("bob", 40));

        FileMemoryStore second = FileMemoryStore.of(tmp);
        assertThat(second.get("s", "k")).contains(new Profile("bob", 40));
        assertThat(second.keys("s")).containsExactly("k");
    }
}
