package io.tracegraph.memory.window;

import io.tracegraph.connectors.llm.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BufferWindowMemoryTest {

    @Test
    void storesUpToMaxMessages() {
        BufferWindowMemory mem = BufferWindowMemory.of(3);
        mem.add(ChatMessage.user("a"));
        mem.add(ChatMessage.user("b"));
        mem.add(ChatMessage.user("c"));
        assertThat(mem.messages()).hasSize(3);
    }

    @Test
    void evictsOldestWhenFull() {
        BufferWindowMemory mem = BufferWindowMemory.of(2);
        mem.add(ChatMessage.user("first"));
        mem.add(ChatMessage.user("second"));
        mem.add(ChatMessage.user("third"));
        assertThat(mem.messages()).hasSize(2);
        assertThat(mem.messages().get(0).content()).isEqualTo("second");
        assertThat(mem.messages().get(1).content()).isEqualTo("third");
    }

    @Test
    void clearResetsBuffer() {
        BufferWindowMemory mem = BufferWindowMemory.of(5);
        mem.add(ChatMessage.user("hi"));
        mem.clear();
        assertThat(mem.messages()).isEmpty();
        assertThat(mem.size()).isZero();
    }

    @Test
    void rejectsZeroMaxMessages() {
        assertThatThrownBy(() -> BufferWindowMemory.of(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
