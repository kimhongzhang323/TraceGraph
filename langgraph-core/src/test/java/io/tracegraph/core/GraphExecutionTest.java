package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphExecutionTest {

    record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
        OrderState withValid(boolean v)   { return new OrderState(id, v, charged, shipped); }
        OrderState withCharged(boolean c) { return new OrderState(id, valid, c, shipped); }
        OrderState withShipped(boolean s) { return new OrderState(id, valid, charged, s); }
    }

    @Test
    void linearOrderHappyPath() {
        Graph<OrderState> graph = Graph.<OrderState>builder()
                .node("validate", (state, ctx) -> state.withValid(true))
                .node("charge",   (state, ctx) -> state.withCharged(true))
                .node("ship",     (state, ctx) -> state.withShipped(true))
                .entry("validate")
                .edge("validate", "charge", OrderState::valid)
                .edge("charge", "ship")
                .terminal("ship")
                .build();

        ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));

        assertThat(result.path()).containsExactly("validate", "charge", "ship");
        assertThat(result.status()).isEqualTo(Status.COMPLETED);
        assertThat(result.finalState().valid()).isTrue();
        assertThat(result.finalState().charged()).isTrue();
        assertThat(result.finalState().shipped()).isTrue();
        assertThat(result.error()).isNull();
    }

    @Test
    void conditionalEdgeFalseBranchTakesSecondEdge() {
        Graph<OrderState> graph = Graph.<OrderState>builder()
                .node("validate", (state, ctx) -> state.withValid(false))
                .node("charge",   (state, ctx) -> state.withCharged(true))
                .node("reject",   (state, ctx) -> state)
                .entry("validate")
                .edge("validate", "charge", OrderState::valid)
                .edge("validate", "reject")
                .terminal("charge")
                .terminal("reject")
                .build();

        ExecutionResult<OrderState> result = graph.run(new OrderState("o-2", false, false, false));

        assertThat(result.path()).containsExactly("validate", "reject");
        assertThat(result.status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void edgesEvaluatedInDeclarationOrder() {
        Graph<String> graph = Graph.<String>builder()
                .node("start", (s, ctx) -> s)
                .node("a", (s, ctx) -> "a")
                .node("b", (s, ctx) -> "b")
                .entry("start")
                .edge("start", "a", s -> true)
                .edge("start", "b", s -> true)
                .terminal("a")
                .terminal("b")
                .build();

        ExecutionResult<String> result = graph.run("seed");
        assertThat(result.path()).containsExactly("start", "a");
    }

    @Test
    void nodeThrowingProducesFailedStatus() {
        Graph<String> graph = Graph.<String>builder()
                .node("ok", (s, ctx) -> s)
                .node("boom", (s, ctx) -> { throw new IllegalStateException("kaboom"); })
                .entry("ok")
                .edge("ok", "boom")
                .terminal("boom")
                .build();

        ExecutionResult<String> result = graph.run("hi");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.path()).containsExactly("ok", "boom");
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(((NodeExecutionException) result.error()).nodeName()).isEqualTo("boom");
        assertThat(result.error().getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void selfLoopHitsMaxStepGuard() {
        Graph<Integer> graph = Graph.<Integer>builder()
                .node("loop", (s, ctx) -> s + 1)
                .entry("loop")
                .edge("loop", "loop")
                .maxSteps(5)
                .build();

        ExecutionResult<Integer> result = graph.run(0);

        assertThat(result.status()).isEqualTo(Status.HALTED);
        assertThat(result.path()).hasSize(5);
        assertThat(result.finalState()).isEqualTo(5);
    }
}
