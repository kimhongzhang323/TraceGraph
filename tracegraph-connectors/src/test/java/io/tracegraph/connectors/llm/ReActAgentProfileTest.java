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

class ReActAgentProfileTest {

    record AgentState(List<ChatMessage> messages, String finalAnswer) {
        AgentState withMessages(List<ChatMessage> msgs) {
            return new AgentState(msgs, finalAnswer);
        }
    }

    private static ReActAgent.Builder<AgentState> baseBuilder(MockLlmClient mock) {
        return ReActAgent.<AgentState>builder()
                .client(mock)
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
                });
    }

    @Test
    void profilePrependsSystemPromptToEveryRequest() {
        MockLlmClient mock = MockLlmClient.constant("done");
        AgentProfile<AgentState> profile = AgentProfile.<AgentState>builder()
                .name("researcher")
                .systemPrompt("You are a researcher.")
                .tool(ToolDefinition.of("noop", "noop"), args -> "")
                .build();

        Graph<AgentState> graph = baseBuilder(mock)
                .profile(profile)
                .build()
                .buildGraph();

        AgentState initial = new AgentState(List.of(ChatMessage.user("hi")), null);
        ExecutionResult<AgentState> result = graph.run(initial);

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(mock.calls()).hasSize(1);
        ChatMessage first = mock.calls().get(0).messages().get(0);
        assertThat(first.role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(first.content()).isEqualTo("You are a researcher.");
    }

    @Test
    void profileToolsReplacePriorToolRegistrations() {
        MockLlmClient mock = MockLlmClient.constant("ok");
        AgentProfile<AgentState> profile = AgentProfile.<AgentState>builder()
                .name("writer")
                .systemPrompt("You write.")
                .tool(ToolDefinition.of("compose", "Compose text"), args -> "wrote")
                .build();

        Graph<AgentState> graph = baseBuilder(mock)
                .tool(ToolDefinition.of("oldTool", "Should be discarded"), args -> "discarded")
                .profile(profile)
                .build()
                .buildGraph();

        AgentState initial = new AgentState(List.of(ChatMessage.user("hi")), null);
        graph.run(initial);

        assertThat(mock.calls()).hasSize(1);
        List<ToolDefinition> tools = mock.calls().get(0).tools();
        assertThat(tools).extracting(ToolDefinition::name).containsExactly("compose");
        assertThat(tools).extracting(ToolDefinition::name).doesNotContain("oldTool");
    }

    @Test
    void twoAgentsWithDifferentProfilesCannotSeeEachOthersTools() {
        // Same underlying LLM client, two agents with disjoint tool sets.
        MockLlmClient sharedMock = MockLlmClient.constant("done");

        AgentProfile<AgentState> alice = AgentProfile.<AgentState>builder()
                .name("alice")
                .systemPrompt("You are alice.")
                .tool(ToolDefinition.of("search", "Search"), args -> "alice-result")
                .build();

        AgentProfile<AgentState> bob = AgentProfile.<AgentState>builder()
                .name("bob")
                .systemPrompt("You are bob.")
                .tool(ToolDefinition.of("write", "Write"), args -> "bob-result")
                .build();

        Graph<AgentState> aliceGraph = baseBuilder(sharedMock).profile(alice).build().buildGraph();
        Graph<AgentState> bobGraph = baseBuilder(sharedMock).profile(bob).build().buildGraph();

        aliceGraph.run(new AgentState(List.of(ChatMessage.user("q")), null));
        int afterAlice = sharedMock.calls().size();
        bobGraph.run(new AgentState(List.of(ChatMessage.user("q")), null));

        List<LlmRequest> calls = sharedMock.calls();
        // Alice's request: only "search" tool visible
        List<String> aliceToolNames = calls.get(afterAlice - 1).tools().stream()
                .map(ToolDefinition::name).toList();
        assertThat(aliceToolNames).containsExactly("search");

        // Bob's request: only "write" tool visible
        List<String> bobToolNames = calls.get(afterAlice).tools().stream()
                .map(ToolDefinition::name).toList();
        assertThat(bobToolNames).containsExactly("write");
    }

    @Test
    void profileToolExecutionRoutesToProfileImplementation() {
        var toolCalls = List.of(new ToolCall("c-1", "calc", "{}"));
        MockLlmClient mock = MockLlmClient.withToolCalls(toolCalls, "the answer is 42");

        AgentProfile<AgentState> profile = AgentProfile.<AgentState>builder()
                .name("solver")
                .systemPrompt("You solve.")
                .tool(ToolDefinition.of("calc", "Calculate",
                                Map.of("type", "object")),
                        args -> "42")
                .build();

        Graph<AgentState> graph = baseBuilder(mock)
                .profile(profile)
                .build()
                .buildGraph();

        ExecutionResult<AgentState> result = graph.run(
                new AgentState(List.of(ChatMessage.user("solve")), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().finalAnswer()).isEqualTo("the answer is 42");

        // Second LLM call has the tool result message with the value from the profile's tool
        LlmRequest secondCall = mock.calls().get(1);
        boolean toolResultPresent = secondCall.messages().stream()
                .anyMatch(m -> m.role() == ChatMessage.Role.TOOL && m.content().equals("42"));
        assertThat(toolResultPresent).isTrue();
    }

    @Test
    void profileEnablesAgentBuildWithNoStandaloneToolRegistration() {
        // Without profile, builder requires at least one tool().
        // With profile, the profile's tools satisfy that requirement.
        MockLlmClient mock = MockLlmClient.constant("ok");
        AgentProfile<AgentState> profile = AgentProfile.<AgentState>builder()
                .name("p")
                .systemPrompt("p")
                .tool(ToolDefinition.of("only", "only"), args -> "")
                .build();

        ReActAgent<AgentState> agent = baseBuilder(mock).profile(profile).build();
        assertThat(agent).isNotNull();
    }

    @Test
    void profileWithoutToolsStillRequiresAtLeastOneTool() {
        MockLlmClient mock = MockLlmClient.constant("ok");
        AgentProfile<AgentState> emptyProfile = AgentProfile.<AgentState>builder()
                .name("p")
                .systemPrompt("p")
                .build();

        assertThatThrownBy(() -> baseBuilder(mock).profile(emptyProfile).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one tool");
    }

    @Test
    void blankSystemPromptDoesNotInjectSystemMessage() {
        MockLlmClient mock = MockLlmClient.constant("done");
        AgentProfile<AgentState> profile = AgentProfile.<AgentState>builder()
                .name("p")
                .systemPrompt("")
                .tool(ToolDefinition.of("t", "t"), args -> "")
                .build();

        Graph<AgentState> graph = baseBuilder(mock).profile(profile).build().buildGraph();
        graph.run(new AgentState(List.of(ChatMessage.user("hi")), null));

        List<ChatMessage> sent = mock.calls().get(0).messages();
        assertThat(sent).extracting(ChatMessage::role)
                .doesNotContain(ChatMessage.Role.SYSTEM);
    }

    @Test
    void builderRejectsNullProfile() {
        assertThatThrownBy(() -> baseBuilder(MockLlmClient.echoing()).profile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("profile");
    }
}
