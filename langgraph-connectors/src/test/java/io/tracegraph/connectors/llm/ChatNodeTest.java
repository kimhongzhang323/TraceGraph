package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatNodeTest {

    record Conversation(String userMessage, String reply) {}

    @Test
    void chatNodeFoldsLlmResponseIntoState() {
        MockLlmClient client = MockLlmClient.echoing();

        ChatNode<Conversation> node = ChatNode.of(client,
                state -> LlmRequest.builder()
                        .model("test")
                        .messages(List.of(ChatMessage.user(state.userMessage())))
                        .build(),
                (state, response) -> new Conversation(state.userMessage(), response.content()));

        Graph<Conversation> g = Graph.<Conversation>builder()
                .node("chat", node)
                .entry("chat").terminal("chat")
                .build();

        ExecutionResult<Conversation> r = g.run(new Conversation("hi", null));

        assertThat(r.finalState().reply()).isEqualTo("hi");
        assertThat(client.calls()).hasSize(1);
        assertThat(client.calls().get(0).model()).isEqualTo("test");
    }
}
