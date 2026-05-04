package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingNodeTest {

    @Test
    void goToBypassesEdges() {
        Graph<String> g = Graph.<String>builder()
                .routingNode("router", (s, ctx) -> NodeResult.goTo("c", s + "R"))
                .node("b", (s, ctx) -> s + "B")
                .node("c", (s, ctx) -> s + "C")
                .edge("router", "b")
                .edge("b", "c").edge("c", "c")
                .entry("router").terminal("c")
                .build();
        ExecutionResult<String> r = g.run("");
        assertThat(r.finalState()).isEqualTo("RC");
    }

    @Test
    void continueFallsThroughToEdges() {
        Graph<String> g = Graph.<String>builder()
                .routingNode("router", (s, ctx) -> NodeResult.of(s + "R"))
                .node("b", (s, ctx) -> s + "B")
                .edge("router", "b").entry("router").terminal("b")
                .build();
        assertThat(g.run("").finalState()).isEqualTo("RB");
    }

    @Test
    void unknownGoToTargetRejectedAtRuntime() {
        Graph<String> g = Graph.<String>builder()
                .routingNode("router", (s, ctx) -> NodeResult.goTo("ghost", s))
                .node("b", (s, ctx) -> s)
                .edge("router", "b").entry("router").terminal("b")
                .build();
        ExecutionResult<String> r = g.run("");
        assertThat(r.status()).isEqualTo(Status.FAILED);
        assertThat(r.error()).isNotNull();
        String errorMsg = r.error().getMessage()
                + (r.error().getCause() != null ? " " + r.error().getCause().getMessage() : "");
        assertThat(errorMsg).containsIgnoringCase("ghost");
    }

    @Test
    void routingNodeWithRetryPolicyIsSupported() {
        Graph<String> g = Graph.<String>builder()
                .routingNode("router", (s, ctx) -> NodeResult.of(s + "R"),
                        RetryPolicy.fixed(2, java.time.Duration.ZERO))
                .node("b", (s, ctx) -> s + "B")
                .edge("router", "b").entry("router").terminal("b")
                .build();
        assertThat(g.run("").finalState()).isEqualTo("RB");
    }
}
