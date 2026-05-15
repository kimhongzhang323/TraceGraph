package io.tracegraph.memory.window;

import io.tracegraph.connectors.llm.ChatMessage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the last {@code maxMessages} turns of a conversation in a fixed-size sliding window.
 *
 * <p>Equivalent to LangChain's {@code ConversationBufferWindowMemory}. Thread-safe — all
 * mutations are synchronised on the instance.
 *
 * <pre>{@code
 * BufferWindowMemory memory = BufferWindowMemory.of(10);
 * memory.add(ChatMessage.user("hello"));
 * memory.add(ChatMessage.assistant("hi there"));
 * List<ChatMessage> history = memory.messages();
 * }</pre>
 */
public final class BufferWindowMemory {

    private final int maxMessages;
    private final Deque<ChatMessage> window;

    private BufferWindowMemory(int maxMessages) {
        if (maxMessages < 1) throw new IllegalArgumentException("maxMessages must be >= 1");
        this.maxMessages = maxMessages;
        this.window = new ArrayDeque<>(maxMessages + 1);
    }

    public static BufferWindowMemory of(int maxMessages) {
        return new BufferWindowMemory(maxMessages);
    }

    /**
     * Add a message, evicting the oldest if the window is full.
     *
     * @param message message to add
     */
    public synchronized void add(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        window.addLast(message);
        while (window.size() > maxMessages) {
            window.pollFirst();
        }
    }

    /**
     * Return all messages currently in the window, in chronological order.
     *
     * @return immutable snapshot
     */
    public synchronized List<ChatMessage> messages() {
        return List.copyOf(window);
    }

    /** Clear all messages. */
    public synchronized void clear() {
        window.clear();
    }

    /** Current number of messages in the window. */
    public synchronized int size() {
        return window.size();
    }

    public int maxMessages() {
        return maxMessages;
    }
}
