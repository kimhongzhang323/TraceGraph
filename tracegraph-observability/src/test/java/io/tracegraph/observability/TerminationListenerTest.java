package io.tracegraph.observability;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TerminationSignalException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminationListenerTest {

    @Test
    void maxTurnsThrowsWhenLimitReached() {
        TerminationListener<Object> listener = TerminationListener.maxTurns(3);

        listener.onEnter("a", "s0");
        listener.onExit("a", "s1");
        listener.onEnter("b", "s1");
        listener.onExit("b", "s2");
        listener.onEnter("c", "s2");

        assertThatThrownBy(() -> listener.onExit("c", "s3"))
                .isInstanceOf(TerminationSignalException.class)
                .hasMessageContaining("3");
    }

    @Test
    void maxTurnsDoesNotThrowBelowLimit() {
        TerminationListener<Object> listener = TerminationListener.maxTurns(5);

        assertThatNoException().isThrownBy(() -> {
            listener.onEnter("a", "s");
            listener.onExit("a", "s");
            listener.onEnter("b", "s");
            listener.onExit("b", "s");
        });
    }

    @Test
    void afterNodeThrowsOnNamedNodeExit() {
        TerminationListener<Object> listener = TerminationListener.afterNode("finish");

        listener.onEnter("a", "s");
        listener.onExit("a", "s");

        assertThatThrownBy(() -> {
            listener.onEnter("finish", "s");
            listener.onExit("finish", "s");
        }).isInstanceOf(TerminationSignalException.class)
                .hasMessageContaining("finish");
    }

    @Test
    void afterNodeIgnoresOtherNodes() {
        TerminationListener<Object> listener = TerminationListener.afterNode("finish");

        assertThatNoException().isThrownBy(() -> {
            listener.onEnter("a", "s");
            listener.onExit("a", "s");
            listener.onEnter("b", "s");
            listener.onExit("b", "s");
        });
    }

    @Test
    void stateMatchesThrowsWhenPredicateTrue() {
        TerminationListener<String> listener = TerminationListener.stateMatches(s -> s.startsWith("DONE"));

        assertThatNoException().isThrownBy(() -> listener.onExit("a", "running"));
        assertThatThrownBy(() -> listener.onExit("b", "DONE-ok"))
                .isInstanceOf(TerminationSignalException.class);
    }

    @Test
    void customPredicateReceivesContext() {
        AtomicInteger seen = new AtomicInteger();
        Predicate<TerminationContext<String>> predicate = ctx -> {
            seen.incrementAndGet();
            return "stop".equals(ctx.afterState());
        };
        TerminationListener<String> listener = new TerminationListener<>(predicate);

        listener.onEnter("a", "go");
        listener.onExit("a", "go");
        listener.onEnter("b", "stop");
        assertThatThrownBy(() -> listener.onExit("b", "stop"))
                .isInstanceOf(TerminationSignalException.class);
        assertThat(seen.get()).isEqualTo(2);
    }

    @Test
    void contextNodesEnteredCountsOnlyEnterEvents() {
        AtomicInteger lastCount = new AtomicInteger();
        Predicate<TerminationContext<String>> predicate = ctx -> {
            lastCount.set(ctx.nodesEnteredSoFar());
            return false;
        };
        TerminationListener<String> listener = new TerminationListener<>(predicate);

        listener.onEnter("a", "s");
        listener.onExit("a", "s");
        assertThat(lastCount.get()).isEqualTo(1);

        listener.onEnter("b", "s");
        listener.onExit("b", "s");
        assertThat(lastCount.get()).isEqualTo(2);

        listener.onEnter("c", "s");
        listener.onExit("c", "s");
        assertThat(lastCount.get()).isEqualTo(3);
    }

    @Test
    void contextCarriesNodeName() {
        AtomicInteger seen = new AtomicInteger();
        Predicate<TerminationContext<String>> predicate = ctx -> {
            seen.incrementAndGet();
            assertThat(ctx.nodeName()).isEqualTo("alpha");
            assertThat(ctx.afterState()).isEqualTo("payload");
            return false;
        };
        TerminationListener<String> listener = new TerminationListener<>(predicate);

        listener.onEnter("alpha", "payload");
        listener.onExit("alpha", "payload");
        assertThat(seen.get()).isEqualTo(1);
    }

    @Test
    void constructorRejectsNullPredicate() {
        assertThatThrownBy(() -> new TerminationListener<>(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void maxTurnsRejectsZero() {
        assertThatThrownBy(() -> TerminationListener.maxTurns(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxTurnsRejectsNegative() {
        assertThatThrownBy(() -> TerminationListener.maxTurns(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void afterNodeRejectsNullName() {
        assertThatThrownBy(() -> TerminationListener.afterNode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void afterNodeRejectsBlankName() {
        assertThatThrownBy(() -> TerminationListener.afterNode("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateMatchesRejectsNullPredicate() {
        assertThatThrownBy(() -> TerminationListener.stateMatches(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void composedTerminationSignalPropagatesThroughListenersCompose() {
        TerminationListener<Object> listener = TerminationListener.maxTurns(1);
        LlmCostListener tracker = new LlmCostListener();
        NodeListener composed = Listeners.compose(tracker, listener);

        composed.onEnter("a", "s");
        assertThatThrownBy(() -> composed.onExit("a", "s"))
                .isInstanceOf(TerminationSignalException.class);
    }

    @Test
    void terminationContextRecordExposesAccessors() {
        TerminationContext<String> ctx = new TerminationContext<>("n", 4, "state-value");
        assertThat(ctx.nodeName()).isEqualTo("n");
        assertThat(ctx.nodesEnteredSoFar()).isEqualTo(4);
        assertThat(ctx.afterState()).isEqualTo("state-value");
    }

    record RouterState(int round) {
        RouterState inc() { return new RouterState(round + 1); }
    }

    @Test
    void infiniteSupervisorLoopHaltsCleanlyAfterFiveRoundsViaMaxTurns() {
        TerminationListener<RouterState> listener = TerminationListener.maxTurns(10);

        Graph<RouterState> graph = Graph.<RouterState>builder()
                .node("router", (state, ctx) -> state)
                .node("agent", (state, ctx) -> state.inc())
                .entry("router")
                .edge("router", "agent")
                .edge("agent", "router")
                .listener(listener)
                .maxSteps(1000)
                .build();

        ExecutionResult<RouterState> result = graph.run(new RouterState(0));

        assertThat(result.status()).isEqualTo(Status.TERMINATED);
        assertThat(result.error()).isNull();
        assertThat(result.finalState().round()).isEqualTo(5);
        assertThat(result.path()).hasSize(10);
    }
}
