package io.tracegraph.connectors.llm;

import io.tracegraph.core.spi.MemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSessionTest {

    private static final class MapMemoryStore implements MemoryStore {
        private final Map<String, Map<String, Object>> scopes = new ConcurrentHashMap<>();

        @Override
        public Optional<Object> get(String scope, String key) {
            return Optional.ofNullable(scopes.getOrDefault(scope, Map.of()).get(key));
        }

        @Override
        public void put(String scope, String key, Object value) {
            scopes.computeIfAbsent(scope, s -> new ConcurrentHashMap<>()).put(key, value);
        }

        @Override
        public boolean delete(String scope, String key) {
            return scopes.getOrDefault(scope, Map.of()).remove(key) != null;
        }

        @Override
        public Set<String> keys(String scope) {
            return Set.copyOf(scopes.getOrDefault(scope, Map.of()).keySet());
        }
    }

    @Test
    void appendAndLoadPreservesOrder() {
        ChatSession session = ChatSession.of(new MapMemoryStore(), "s1");

        session.append(ChatMessage.user("hello"));
        session.append(ChatMessage.assistant("hi there"));
        session.append(ChatMessage.user("how are you?"));

        assertThat(session.messages()).containsExactly(
                ChatMessage.user("hello"),
                ChatMessage.assistant("hi there"),
                ChatMessage.user("how are you?"));
    }

    @Test
    void sessionsAreIsolatedByIdAndScope() {
        MemoryStore store = new MapMemoryStore();
        ChatSession a = ChatSession.of(store, "a");
        ChatSession b = ChatSession.of(store, "b");
        ChatSession otherScope = ChatSession.of(store, "support-bot", "a");

        a.append(ChatMessage.user("for a"));
        b.append(ChatMessage.user("for b"));
        otherScope.append(ChatMessage.user("for other scope"));

        assertThat(a.messages()).containsExactly(ChatMessage.user("for a"));
        assertThat(b.messages()).containsExactly(ChatMessage.user("for b"));
        assertThat(otherScope.messages()).containsExactly(ChatMessage.user("for other scope"));
    }

    @Test
    void historySurvivesReattachingToTheStore() {
        MemoryStore store = new MapMemoryStore();
        ChatSession.of(store, "s1").append(ChatMessage.user("persisted"));

        assertThat(ChatSession.of(store, "s1").messages())
                .containsExactly(ChatMessage.user("persisted"));
    }

    @Test
    void emptySessionLoadsAsEmptyList() {
        assertThat(ChatSession.of(new MapMemoryStore(), "fresh").messages()).isEmpty();
    }

    @Test
    void clearRemovesHistory() {
        MemoryStore store = new MapMemoryStore();
        ChatSession session = ChatSession.of(store, "s1");
        session.append(ChatMessage.user("gone soon"));

        session.clear();

        assertThat(session.messages()).isEmpty();
        assertThat(ChatSession.of(store, "s1").messages()).isEmpty();
    }

    @Test
    void concurrentAppendsOnTheSameInstanceLoseNothing() throws Exception {
        ChatSession session = ChatSession.of(new MapMemoryStore(), "s1");
        int threads = 8;
        int perThread = 25;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            workers.add(Thread.startVirtualThread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    session.append(ChatMessage.user(id + "-" + i));
                }
            }));
        }
        start.countDown();
        for (Thread w : workers) {
            w.join();
        }

        assertThat(session.messages()).hasSize(threads * perThread);
    }

    @Test
    void rejectsBlankSessionId() {
        assertThatThrownBy(() -> ChatSession.of(new MapMemoryStore(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
