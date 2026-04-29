package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentTest {

    /** Simple state carrying the conversation history. */
    record AgentState(List<ChatMessage> messages, String finalAnswer) {
        AgentState withMessages(List<ChatMessage> msgs) {
            return new AgentState(msgs, finalAnswer);
        }
        AgentState withAnswer(String answer) {
            return new AgentState(messages, answer);
        }
    }

    @Test
    void singleToolCallRoundTrip() {
        // Mock LLM: first call returns tool call, second call returns final answer
        var toolCalls = List.of(new ToolCall("call-1", "calculator", "{\"expr\":\"2+2\"}"));
        MockLlmClient mock = MockLlmClient.withToolCalls(toolCalls, "The answer is 4");

        Graph<AgentState> graph = ReActAgent.<AgentState>builder()
                .client(mock)
                .tool(ToolDefinition.of("calculator", "Calculate math expressions",
                                Map.of("type", "object", "properties",
                                        Map.of("expr", Map.of("type", "string")))),
                        args -> "4")
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(state.messages()))
                .responseFolder((state, response) -> {
                    var msgs = new ArrayList<>(state.messages());
                    if (response.hasToolCalls()) {
                        msgs.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
                    } else {
                        msgs.add(ChatMessage.assistant(response.content()));
                    }
                    String answer = response.hasToolCalls() ? state.finalAnswer() : response.content();
                    return new AgentState(msgs, answer);
                })
                .toolResultFolder((state, results) -> {
                    var msgs = new ArrayList<>(state.messages());
                    for (ToolResult r : results) {
                        msgs.add(ChatMessage.toolResult(r.callId(), r.content()));
                    }
                    return state.withMessages(msgs);
                })
                .maxIterations(5)
                .build()
                .buildGraph();

        AgentState initial = new AgentState(
                List.of(ChatMessage.user("What is 2+2?")), null);

        ExecutionResult<AgentState> result = graph.run(initial);

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().finalAnswer()).isEqualTo("The answer is 4");
        // Two LLM calls: one returning tool_calls, one returning final answer
        assertThat(mock.calls()).hasSize(2);
        // Verify the second call includes tool result messages
        LlmRequest secondCall = mock.calls().get(1);
        assertThat(secondCall.messages().stream()
                .anyMatch(m -> m.role() == ChatMessage.Role.TOOL)).isTrue();
    }

    @Test
    void toolExecutionErrorIsWrapped() {
        var toolCalls = List.of(new ToolCall("call-1", "failing_tool", "{}"));
        MockLlmClient mock = MockLlmClient.withToolCalls(toolCalls, "I couldn't do it");

        Graph<AgentState> graph = ReActAgent.<AgentState>builder()
                .client(mock)
                .tool(ToolDefinition.of("failing_tool", "A tool that always fails"),
                        args -> { throw new RuntimeException("boom"); })
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(state.messages()))
                .responseFolder((state, response) -> {
                    var msgs = new ArrayList<>(state.messages());
                    if (response.hasToolCalls()) {
                        msgs.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
                    } else {
                        msgs.add(ChatMessage.assistant(response.content()));
                    }
                    return new AgentState(msgs, response.hasToolCalls() ? null : response.content());
                })
                .toolResultFolder((state, results) -> {
                    var msgs = new ArrayList<>(state.messages());
                    for (ToolResult r : results) {
                        msgs.add(ChatMessage.toolResult(r.callId(), r.content()));
                    }
                    return state.withMessages(msgs);
                })
                .build()
                .buildGraph();

        AgentState initial = new AgentState(List.of(ChatMessage.user("fail")), null);
        ExecutionResult<AgentState> result = graph.run(initial);

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        // The tool error message was fed back; LLM then gave final answer
        LlmRequest secondCall = mock.calls().get(1);
        boolean hasErrorResult = secondCall.messages().stream()
                .anyMatch(m -> m.role() == ChatMessage.Role.TOOL
                        && m.content().contains("RuntimeException: boom"));
        assertThat(hasErrorResult).isTrue();
    }

    @Test
    void noToolsCausesDirectCompletion() {
        // LLM returns a direct response (no tool calls)
        MockLlmClient mock = MockLlmClient.constant("Direct answer");

        Graph<AgentState> graph = ReActAgent.<AgentState>builder()
                .client(mock)
                .tool(ToolDefinition.of("unused", "Never used"), args -> "")
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(state.messages()))
                .responseFolder((state, response) -> {
                    var msgs = new ArrayList<>(state.messages());
                    msgs.add(ChatMessage.assistant(response.content()));
                    return new AgentState(msgs, response.content());
                })
                .toolResultFolder((state, results) -> state)
                .build()
                .buildGraph();

        AgentState initial = new AgentState(List.of(ChatMessage.user("hi")), null);
        ExecutionResult<AgentState> result = graph.run(initial);

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().finalAnswer()).isEqualTo("Direct answer");
        // Only one LLM call — no tool loop
        assertThat(mock.calls()).hasSize(1);
    }

    @Test
    void builderRejectsNoTools() {
        assertThatThrownBy(() -> ReActAgent.<AgentState>builder()
                .client(MockLlmClient.echoing())
                .requestFactory(s -> LlmRequest.builder().model("x").messages(s.messages()))
                .responseFolder((s, r) -> s)
                .toolResultFolder((s, r) -> s)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one tool");
    }

    @Test
    void builderRejectsNullClient() {
        assertThatThrownBy(() -> ReActAgent.<AgentState>builder()
                .tool(ToolDefinition.of("t", "d"), a -> "")
                .requestFactory(s -> LlmRequest.builder().model("x").messages(s.messages()))
                .responseFolder((s, r) -> s)
                .toolResultFolder((s, r) -> s)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client");
    }
}
