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

class SupervisorAgentTest {

    record SupervisorState(List<String> log, String selectedAgent) {
        SupervisorState withLog(List<String> log) { return new SupervisorState(log, selectedAgent); }
        SupervisorState withAgent(String agent) { return new SupervisorState(log, agent); }
    }

    private Graph<SupervisorState> trivialAgent(String agentName) {
        return Graph.<SupervisorState>builder()
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
    void routerDispatchesAgentsInOrderThenTerminates() {
        // Router: call 1 → "agent1", call 2 → "agent2", call 3 → "done"
        AtomicInteger callCount = new AtomicInteger();
        MockLlmClient routerMock = new MockLlmClient(req -> {
            String selected = switch (callCount.incrementAndGet()) {
                case 1 -> "agent1";
                case 2 -> "agent2";
                default -> "done";
            };
            return new LlmResponse(selected, LlmResponse.FinishReason.STOP, new LlmResponse.Usage(5, 5));
        });

        Graph<SupervisorState> graph = SupervisorAgent.<SupervisorState>builder()
                .routerClient(routerMock)
                .agent("agent1", trivialAgent("agent1"))
                .agent("agent2", trivialAgent("agent2"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("coordinate"))))
                .responseFolder((state, response) -> state.withAgent(response.content()))
                .agentSelector(SupervisorState::selectedAgent)
                .maxRounds(5)
                .build()
                .buildGraph();

        ExecutionResult<SupervisorState> result =
                graph.run(new SupervisorState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:agent1", "ran:agent2");
        assertThat(routerMock.calls()).hasSize(3);
    }

    @Test
    void routerTerminatesImmediatelyWhenDoneOnFirstCall() {
        MockLlmClient routerMock = MockLlmClient.constant("done");

        Graph<SupervisorState> graph = SupervisorAgent.<SupervisorState>builder()
                .routerClient(routerMock)
                .agent("agent1", trivialAgent("agent1"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("nothing to do"))))
                .responseFolder((state, response) -> state.withAgent(response.content()))
                .agentSelector(SupervisorState::selectedAgent)
                .build()
                .buildGraph();

        ExecutionResult<SupervisorState> result =
                graph.run(new SupervisorState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
        assertThat(routerMock.calls()).hasSize(1);
    }

    @Test
    void routerTreatsUnknownAgentNameAsTermination() {
        MockLlmClient routerMock = MockLlmClient.constant("nonexistent-agent");

        Graph<SupervisorState> graph = SupervisorAgent.<SupervisorState>builder()
                .routerClient(routerMock)
                .agent("agent1", trivialAgent("agent1"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("go"))))
                .responseFolder((state, response) -> state.withAgent(response.content()))
                .agentSelector(SupervisorState::selectedAgent)
                .build()
                .buildGraph();

        ExecutionResult<SupervisorState> result =
                graph.run(new SupervisorState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
    }

    @Test
    void routerTreatsNullSelectorResultAsTermination() {
        MockLlmClient routerMock = MockLlmClient.constant("ignored");

        Graph<SupervisorState> graph = SupervisorAgent.<SupervisorState>builder()
                .routerClient(routerMock)
                .agent("agent1", trivialAgent("agent1"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("go"))))
                .responseFolder((state, response) -> state.withAgent(null))
                .agentSelector(SupervisorState::selectedAgent)
                .build()
                .buildGraph();

        ExecutionResult<SupervisorState> result =
                graph.run(new SupervisorState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).isEmpty();
    }

    @Test
    void sameAgentCalledMultipleRoundsBeforeDone() {
        AtomicInteger callCount = new AtomicInteger();
        MockLlmClient routerMock = new MockLlmClient(req -> {
            String selected = callCount.incrementAndGet() <= 2 ? "agent1" : "done";
            return new LlmResponse(selected, LlmResponse.FinishReason.STOP, new LlmResponse.Usage(5, 5));
        });

        Graph<SupervisorState> graph = SupervisorAgent.<SupervisorState>builder()
                .routerClient(routerMock)
                .agent("agent1", trivialAgent("agent1"))
                .requestFactory(state -> LlmRequest.builder()
                        .model("gpt-4")
                        .messages(List.of(ChatMessage.user("repeat"))))
                .responseFolder((state, response) -> state.withAgent(response.content()))
                .agentSelector(SupervisorState::selectedAgent)
                .maxRounds(5)
                .build()
                .buildGraph();

        ExecutionResult<SupervisorState> result =
                graph.run(new SupervisorState(new ArrayList<>(), null));

        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().log()).containsExactly("ran:agent1", "ran:agent1");
    }

    @Test
    void builderRejectsNoAgents() {
        assertThatThrownBy(() -> SupervisorAgent.<SupervisorState>builder()
                .routerClient(MockLlmClient.constant("done"))
                .requestFactory(s -> LlmRequest.builder().model("m").messages(List.of()))
                .responseFolder((s, r) -> s)
                .agentSelector(s -> "done")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one agent");
    }

    @Test
    void builderRejectsReservedAgentNameRouter() {
        assertThatThrownBy(() -> SupervisorAgent.<SupervisorState>builder()
                .agent("router", trivialAgent("router")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderRejectsReservedAgentNameDone() {
        assertThatThrownBy(() -> SupervisorAgent.<SupervisorState>builder()
                .agent("done", trivialAgent("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderRejectsDuplicateAgentName() {
        assertThatThrownBy(() -> SupervisorAgent.<SupervisorState>builder()
                .agent("agent1", trivialAgent("agent1"))
                .agent("agent1", trivialAgent("agent1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void builderRejectsNullRouterClient() {
        assertThatThrownBy(() -> SupervisorAgent.<SupervisorState>builder()
                .agent("agent1", trivialAgent("agent1"))
                .requestFactory(s -> LlmRequest.builder().model("m").messages(List.of()))
                .responseFolder((s, r) -> s)
                .agentSelector(s -> "done")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("routerClient");
    }
}
