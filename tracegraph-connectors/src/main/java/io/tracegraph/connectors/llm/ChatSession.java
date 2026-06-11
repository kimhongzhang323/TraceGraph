package io.tracegraph.connectors.llm;

import io.tracegraph.core.spi.MemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable conversation history layered on the {@link MemoryStore} SPI — the bridge between
 * cross-execution memory and chat-shaped LLM requests.
 *
 * <p>Messages append in call order and reload across executions (and processes, when backed by a
 * persistent store such as {@code FileMemoryStore} or {@code JdbcMemoryStore}). The whole history
 * is stored as one value under {@code scope}/{@code sessionId}, so it round-trips through the
 * stores' heterogeneous-value JSON serialization unchanged.
 *
 * <p>Appends on a single instance are atomic (guarded by a lock — read-modify-write against the
 * store). Two instances or processes appending to the same session concurrently can lose messages;
 * give each conversation owner its own session id.
 */
public final class ChatSession {

    private static final String DEFAULT_SCOPE = "tracegraph.session";

    private final MemoryStore store;
    private final String scope;
    private final String sessionId;
    private final ReentrantLock appendLock = new ReentrantLock();

    private ChatSession(MemoryStore store, String scope, String sessionId) {
        this.store = store;
        this.scope = scope;
        this.sessionId = sessionId;
    }

    /** Attaches to session {@code sessionId} in the default scope. */
    public static ChatSession of(MemoryStore store, String sessionId) {
        return of(store, DEFAULT_SCOPE, sessionId);
    }

    /** Attaches to session {@code sessionId} in {@code scope} (one scope per agent/tenant). */
    public static ChatSession of(MemoryStore store, String scope, String sessionId) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(sessionId, "sessionId");
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return new ChatSession(store, scope, sessionId);
    }

    public String sessionId() {
        return sessionId;
    }

    public String scope() {
        return scope;
    }

    /** Appends {@code message} to the history and persists it. */
    public void append(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        appendLock.lock();
        try {
            List<ChatMessage> history = new ArrayList<>(messages());
            history.add(message);
            store.put(scope, sessionId, List.copyOf(history));
        } finally {
            appendLock.unlock();
        }
    }

    /** The full message history in append order; empty for a new session. */
    public List<ChatMessage> messages() {
        return store.get(scope, sessionId)
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    List<ChatMessage> history = (List<ChatMessage>) value;
                    return List.copyOf(history);
                })
                .orElse(List.of());
    }

    /** Deletes the stored history. */
    public void clear() {
        store.delete(scope, sessionId);
    }
}
