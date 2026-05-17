package io.tracegraph.e2e;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.ChatNode;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.MockLlmClient;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.observability.CostReport;
import io.tracegraph.observability.LlmCostListener;
import io.tracegraph.observability.Usage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
class LlmCostBreakdownIT {

    record Conversation(String userMessage, String replyFromA, String replyFromB) {
        Conversation withReplyFromA(String r) { return new Conversation(userMessage, r, replyFromB); }
        Conversation withReplyFromB(String r) { return new Conversation(userMessage, replyFromA, r); }
    }

    @Test
    void multiNodeMultiLlmGraphPopulatesPerNodeCostReport() {
        MockLlmClient providerA = new MockLlmClient(req -> new LlmResponse(
                "from-a", LlmResponse.FinishReason.STOP, new LlmResponse.Usage(40, 12)));
        MockLlmClient providerB = new MockLlmClient(req -> new LlmResponse(
                "from-b", LlmResponse.FinishReason.STOP, new LlmResponse.Usage(15, 30)));

        LlmCostListener costListener = new LlmCostListener();

        ChatNode<Conversation> nodeA = ChatNode.of(providerA,
                state -> LlmRequest.builder()
                        .model("provider-a-model")
                        .messages(List.of(ChatMessage.user(state.userMessage())))
                        .build(),
                (state, response) -> state.withReplyFromA(response.content()));
        ChatNode<Conversation> nodeB = ChatNode.of(providerB,
                state -> LlmRequest.builder()
                        .model("provider-b-model")
                        .messages(List.of(ChatMessage.user(state.replyFromA())))
                        .build(),
                (state, response) -> state.withReplyFromB(response.content()));

        Graph<Conversation> graph = Graph.<Conversation>builder()
                .listener(costListener)
                .traceRecorder(costListener)
                .entry("call-a")
                .node("call-a", nodeA)
                .node("call-b", nodeB)
                .edge("call-a", "call-b")
                .terminal("call-b")
                .build();

        ExecutionResult<Conversation> r1 = graph.run(new Conversation("hi", null, null), "exec-1");
        ExecutionResult<Conversation> r2 = graph.run(new Conversation("hi again", null, null), "exec-2");
        assertThat(r1.finalState().replyFromA()).isEqualTo("from-a");
        assertThat(r1.finalState().replyFromB()).isEqualTo("from-b");
        assertThat(r2.finalState().replyFromB()).isEqualTo("from-b");

        CostReport reportExec1 = costListener.snapshot("exec-1");
        assertThat(reportExec1.executionId()).isEqualTo("exec-1");
        assertThat(reportExec1.usageByNode()).containsOnlyKeys("call-a", "call-b");
        assertThat(reportExec1.usageByNode().get("call-a")).isEqualTo(new Usage(40, 12));
        assertThat(reportExec1.usageByNode().get("call-b")).isEqualTo(new Usage(15, 30));
        assertThat(reportExec1.totalUsage()).isEqualTo(new Usage(55, 42));

        CostReport reportExec2 = costListener.snapshot("exec-2");
        assertThat(reportExec2.totalUsage()).isEqualTo(new Usage(55, 42));
        assertThat(reportExec2.usageByNode().get("call-a")).isEqualTo(new Usage(40, 12));
        assertThat(reportExec2.usageByNode().get("call-b")).isEqualTo(new Usage(15, 30));

        assertThat(costListener.nodePromptTokens("call-a")).isEqualTo(80);
        assertThat(costListener.nodeCompletionTokens("call-b")).isEqualTo(60);
    }
}
