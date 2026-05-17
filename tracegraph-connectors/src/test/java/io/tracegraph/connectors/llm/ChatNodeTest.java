package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.LlmCallInfo;
import io.tracegraph.core.spi.NodeListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    @Test
    void chatNodeReportsLlmCallInfoWithSystemModelAndFinishReason() {
        MockLlmClient client = MockLlmClient.constant("hello");

        ChatNode<String> node = ChatNode.of(client,
                state -> LlmRequest.builder()
                        .model("gpt-4o-mini")
                        .messages(List.of(ChatMessage.user("ping")))
                        .build(),
                (state, response) -> response.content());

        List<LlmCallInfo> captured = new ArrayList<>();
        NodeListener listener = new NodeListener() {
            @Override
            public void onLlmCall(String nodeName, LlmCallInfo info) {
                captured.add(info);
            }
        };

        Graph<String> g = Graph.<String>builder()
                .node("chat", node)
                .entry("chat").terminal("chat")
                .listener(listener)
                .build();

        g.run("");

        assertThat(captured).hasSize(1);
        LlmCallInfo info = captured.get(0);
        assertThat(info.system()).isEqualTo("mock");
        assertThat(info.model()).isEqualTo("gpt-4o-mini");
        assertThat(info.promptTokens()).isEqualTo(0);
        assertThat(info.completionTokens()).isEqualTo("hello".length());
        assertThat(info.finishReason()).isEqualTo("stop");
    }
}
