package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageContentBlockTest {

    @Test
    void userWithImagesIncludesContentBlocks() {
        List<ContentBlock> blocks = List.of(ContentBlock.image("image/png", "abc123"));
        ChatMessage msg = ChatMessage.userWithImages("look at this", blocks);
        assertThat(msg.role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(msg.content()).isEqualTo("look at this");
        assertThat(msg.contentBlocks()).hasSize(1);
        assertThat(msg.contentBlocks().get(0)).isInstanceOf(ContentBlock.ImageBlock.class);
    }

    @Test
    void existingFactoryMethodsHaveEmptyContentBlocks() {
        assertThat(ChatMessage.user("hi").contentBlocks()).isEmpty();
        assertThat(ChatMessage.system("sys").contentBlocks()).isEmpty();
        assertThat(ChatMessage.assistant("ok").contentBlocks()).isEmpty();
        assertThat(ChatMessage.toolResult("id1", "result").contentBlocks()).isEmpty();
        assertThat(ChatMessage.assistantWithToolCalls("", List.of()).contentBlocks()).isEmpty();
    }

    @Test
    void contentBlocksDefensivelyCopied() {
        List<ContentBlock> mutable = new ArrayList<>();
        mutable.add(ContentBlock.text("first"));
        ChatMessage msg = ChatMessage.userWithImages("text", mutable);
        mutable.add(ContentBlock.text("second"));
        assertThat(msg.contentBlocks()).hasSize(1);
    }
}
