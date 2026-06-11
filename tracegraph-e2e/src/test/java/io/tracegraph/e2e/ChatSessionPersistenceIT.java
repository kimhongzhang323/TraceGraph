package io.tracegraph.e2e;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.ChatSession;
import io.tracegraph.memory.FileMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionPersistenceIT {

    @Test
    void historySurvivesAFreshStoreOnTheSameDirectory(@TempDir Path dir) {
        ChatSession first = ChatSession.of(FileMemoryStore.of(dir), "support-42");
        first.append(ChatMessage.user("my order is missing"));
        first.append(ChatMessage.assistantWithToolCalls("checking",
                List.of(new io.tracegraph.connectors.llm.ToolCall("c1", "lookupOrder", "{\"id\":42}"))));
        first.append(ChatMessage.toolResult("c1", "order shipped yesterday"));

        ChatSession reloaded = ChatSession.of(FileMemoryStore.of(dir), "support-42");

        assertThat(reloaded.messages()).hasSize(3);
        assertThat(reloaded.messages().get(0)).isEqualTo(ChatMessage.user("my order is missing"));
        assertThat(reloaded.messages().get(1).toolCalls()).hasSize(1);
        assertThat(reloaded.messages().get(2).toolCallId()).isEqualTo("c1");
    }
}
