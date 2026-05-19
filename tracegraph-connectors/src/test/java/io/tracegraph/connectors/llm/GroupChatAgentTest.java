package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupChatAgentTest {

    record ChatState(List<String> log) {
        ChatState withLog(List<String> log) { return new ChatState(log); }
    }

    private Graph<ChatState> trivialAgent(String agentName) {
        return Graph.<ChatState>builder()
                .node("run", (state, ctx) -> {
                    var log = new ArrayList<>(state.log());
                    log.add("ran:" + agentName);
                    return state.withLog(log);
                })
                .entry("run")
                .terminal("run")
                .build();
    }

    @Test
    void roundRobinRotatesABCAThenTerminatesOnRoundFour() {
        // Termination predicate fires after the 4th agent run (when log.size() == 4).
        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .agent("b", trivialAgent("b"))
                .agent("c", trivialAgent("c"))
                .speakerSelector(SpeakerSelector.roundRobin())
                .terminationPredicate(state -> state.log().size() >= 4)
                .maxRounds(10)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log())
                .containsExactly("ran:a", "ran:b", "ran:c", "ran:a");
    }

    @Test
    void roundRobinCyclesBeyondAgentListLength() {
        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .agent("b", trivialAgent("b"))
                .speakerSelector(SpeakerSelector.roundRobin())
                .terminationPredicate(state -> state.log().size() >= 5)
                .maxRounds(10)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log())
                .containsExactly("ran:a", "ran:b", "ran:a", "ran:b", "ran:a");
    }

    @Test
    void terminationPredicateTrueAtEntryTerminatesImmediately() {
        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .speakerSelector(SpeakerSelector.roundRobin())
                .terminationPredicate(state -> true)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
    }

    @Test
    void llmSpeakerSelectorReadsSpeakerNameFromResponse() {
        // LLM selector picks "a", "b", "a" — predicate halts after 3 runs.
        AtomicInteger callCount = new AtomicInteger();
        MockLlmClient selectorMock = new MockLlmClient(req -> {
            String selected = switch (callCount.incrementAndGet()) {
                case 1 -> "a";
                case 2 -> "b";
                default -> "a";
            };
            return new LlmResponse(selected, LlmResponse.FinishReason.STOP, new LlmResponse.Usage(1, 1));
        });

        SpeakerSelector<ChatState> selector = SpeakerSelector.llm(
                selectorMock,
                state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("pick next speaker"))),
                LlmResponse::content);

        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .agent("b", trivialAgent("b"))
                .speakerSelector(selector)
                .terminationPredicate(state -> state.log().size() >= 3)
                .maxRounds(10)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log())
                .containsExactly("ran:a", "ran:b", "ran:a");
        assertThat(selectorMock.calls()).hasSize(3);
    }

    @Test
    void llmSpeakerSelectorUnknownNameTerminates() {
        MockLlmClient selectorMock = MockLlmClient.constant("nonexistent");

        SpeakerSelector<ChatState> selector = SpeakerSelector.llm(
                selectorMock,
                state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("pick"))),
                LlmResponse::content);

        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .speakerSelector(selector)
                .terminationPredicate(state -> false)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
    }

    @Test
    void llmSpeakerSelectorNullNameTerminates() {
        MockLlmClient selectorMock = MockLlmClient.constant("");

        SpeakerSelector<ChatState> selector = SpeakerSelector.llm(
                selectorMock,
                state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("pick"))),
                response -> null);

        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .speakerSelector(selector)
                .terminationPredicate(state -> false)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
    }

    @Test
    void selectorReturningDoneTerminates() {
        SpeakerSelector<ChatState> selector = (state, names) -> "done";

        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .speakerSelector(selector)
                .terminationPredicate(state -> false)
                .build()
                .buildGraph();

        ExecutionResult<ChatState> result = graph.run(new ChatState(new ArrayList<>()));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
    }

    @Test
    void speakerSelectorReceivesRegisteredAgentNamesInDeclarationOrder() {
        AtomicInteger invocations = new AtomicInteger();
        List<List<String>> observedNames = new ArrayList<>();
        SpeakerSelector<ChatState> selector = (state, names) -> {
            observedNames.add(List.copyOf(names));
            invocations.incrementAndGet();
            return "done";
        };

        Graph<ChatState> graph = GroupChatAgent.<ChatState>builder()
                .agent("zeta", trivialAgent("zeta"))
                .agent("alpha", trivialAgent("alpha"))
                .agent("mu", trivialAgent("mu"))
                .speakerSelector(selector)
                .terminationPredicate(state -> false)
                .build()
                .buildGraph();

        graph.run(new ChatState(new ArrayList<>()));

        assertThat(invocations.get()).isEqualTo(1);
        assertThat(observedNames).hasSize(1);
        assertThat(observedNames.get(0)).containsExactly("zeta", "alpha", "mu");
    }

    @Test
    void builderRejectsNoAgents() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder()
                .speakerSelector(SpeakerSelector.roundRobin())
                .terminationPredicate(s -> true)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one agent");
    }

    @Test
    void builderRejectsReservedAgentNameSpeaker() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder()
                .agent("speaker", trivialAgent("speaker")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderRejectsReservedAgentNameDone() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder()
                .agent("done", trivialAgent("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderRejectsDuplicateAgentName() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .agent("a", trivialAgent("a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void builderRejectsNullSpeakerSelector() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .terminationPredicate(s -> true)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("speakerSelector");
    }

    @Test
    void builderRejectsNullTerminationPredicate() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder()
                .agent("a", trivialAgent("a"))
                .speakerSelector(SpeakerSelector.roundRobin())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("terminationPredicate");
    }

    @Test
    void builderRejectsNonPositiveMaxRounds() {
        assertThatThrownBy(() -> GroupChatAgent.<ChatState>builder().maxRounds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRounds");
    }

    @Test
    void roundRobinFactoryRejectsEmptyNamesList() {
        SpeakerSelector<ChatState> selector = SpeakerSelector.roundRobin();
        assertThatThrownBy(() -> selector.next(new ChatState(List.of()), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
