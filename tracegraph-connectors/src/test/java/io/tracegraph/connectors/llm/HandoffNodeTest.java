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

class HandoffNodeTest {

    record HandoffState(List<String> log, String handoffTarget) {
        HandoffState withLog(List<String> log) { return new HandoffState(log, handoffTarget); }
        HandoffState withTarget(String target) { return new HandoffState(log, target); }
    }

    private Graph<HandoffState> trivialAgent(String agentName) {
        return Graph.<HandoffState>builder()
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
    void handsOffAliceToBobThenDone() {
        AtomicInteger callCount = new AtomicInteger();
        MockLlmClient handoffMock = new MockLlmClient(req -> {
            String selected = switch (callCount.incrementAndGet()) {
                case 1 -> "bob";
                default -> "done";
            };
            return new LlmResponse(selected, LlmResponse.FinishReason.STOP, new LlmResponse.Usage(5, 5));
        });

        Graph<HandoffState> graph = HandoffNode.<HandoffState>builder()
                .client(handoffMock)
                .target("alice", trivialAgent("alice"))
                .target("bob", trivialAgent("bob"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("who is next"))))
                .responseFolder((state, response) -> state.withTarget(response.content()))
                .handoffSelector(HandoffState::handoffTarget)
                .maxHandoffs(5)
                .build()
                .buildGraph();

        ExecutionResult<HandoffState> result =
                graph.run(new HandoffState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:alice", "ran:bob");
        assertThat(handoffMock.calls()).hasSize(2);
    }

    @Test
    void continueRoutesToNextDeclaredAgent() {
        MockLlmClient handoffMock = MockLlmClient.constant("continue");

        Graph<HandoffState> graph = HandoffNode.<HandoffState>builder()
                .client(handoffMock)
                .target("alice", trivialAgent("alice"))
                .target("bob", trivialAgent("bob"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("next?"))))
                .responseFolder((state, response) -> state.withTarget(response.content()))
                .handoffSelector(HandoffState::handoffTarget)
                .maxHandoffs(5)
                .build()
                .buildGraph();

        ExecutionResult<HandoffState> result =
                graph.run(new HandoffState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:alice", "ran:bob");
    }

    @Test
    void continueOnLastAgentTerminates() {
        MockLlmClient handoffMock = MockLlmClient.constant("continue");

        Graph<HandoffState> graph = HandoffNode.<HandoffState>builder()
                .client(handoffMock)
                .target("alice", trivialAgent("alice"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("next?"))))
                .responseFolder((state, response) -> state.withTarget(response.content()))
                .handoffSelector(HandoffState::handoffTarget)
                .build()
                .buildGraph();

        ExecutionResult<HandoffState> result =
                graph.run(new HandoffState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:alice");
    }

    @Test
    void nullTargetTerminates() {
        MockLlmClient handoffMock = MockLlmClient.constant("ignored");

        Graph<HandoffState> graph = HandoffNode.<HandoffState>builder()
                .client(handoffMock)
                .target("alice", trivialAgent("alice"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("next?"))))
                .responseFolder((state, response) -> state.withTarget(null))
                .handoffSelector(HandoffState::handoffTarget)
                .build()
                .buildGraph();

        ExecutionResult<HandoffState> result =
                graph.run(new HandoffState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:alice");
    }

    @Test
    void unknownTargetTerminates() {
        MockLlmClient handoffMock = MockLlmClient.constant("ghost-agent");

        Graph<HandoffState> graph = HandoffNode.<HandoffState>builder()
                .client(handoffMock)
                .target("alice", trivialAgent("alice"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("next?"))))
                .responseFolder((state, response) -> state.withTarget(response.content()))
                .handoffSelector(HandoffState::handoffTarget)
                .build()
                .buildGraph();

        ExecutionResult<HandoffState> result =
                graph.run(new HandoffState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:alice");
    }

    @Test
    void agentCanHandOffBackToItselfThenTerminate() {
        AtomicInteger callCount = new AtomicInteger();
        MockLlmClient handoffMock = new MockLlmClient(req -> {
            String selected = callCount.incrementAndGet() <= 2 ? "alice" : "done";
            return new LlmResponse(selected, LlmResponse.FinishReason.STOP, new LlmResponse.Usage(5, 5));
        });

        Graph<HandoffState> graph = HandoffNode.<HandoffState>builder()
                .client(handoffMock)
                .target("alice", trivialAgent("alice"))
                .target("bob", trivialAgent("bob"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("next?"))))
                .responseFolder((state, response) -> state.withTarget(response.content()))
                .handoffSelector(HandoffState::handoffTarget)
                .maxHandoffs(5)
                .build()
                .buildGraph();

        ExecutionResult<HandoffState> result =
                graph.run(new HandoffState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:alice", "ran:alice", "ran:alice");
    }

    @Test
    void builderRejectsNoTargets() {
        assertThatThrownBy(() -> HandoffNode.<HandoffState>builder()
                .client(MockLlmClient.constant("done"))
                .requestFactory(s -> LlmRequest.builder().model("m").messages(List.of()))
                .responseFolder((s, r) -> s)
                .handoffSelector(s -> "done")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one target");
    }

    @Test
    void builderRejectsReservedTargetNameContinue() {
        assertThatThrownBy(() -> HandoffNode.<HandoffState>builder()
                .target("continue", trivialAgent("continue")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderRejectsReservedTargetNameDone() {
        assertThatThrownBy(() -> HandoffNode.<HandoffState>builder()
                .target("done", trivialAgent("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderRejectsDuplicateTargetName() {
        assertThatThrownBy(() -> HandoffNode.<HandoffState>builder()
                .target("alice", trivialAgent("alice"))
                .target("alice", trivialAgent("alice")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void builderRejectsNullClient() {
        assertThatThrownBy(() -> HandoffNode.<HandoffState>builder()
                .target("alice", trivialAgent("alice"))
                .requestFactory(s -> LlmRequest.builder().model("m").messages(List.of()))
                .responseFolder((s, r) -> s)
                .handoffSelector(s -> "done")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client");
    }
}
