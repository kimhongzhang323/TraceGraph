package io.tracegraph.core;

import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TerminationSignalException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TerminationSignalTest {

    record CounterState(int count) {
        CounterState inc() { return new CounterState(count + 1); }
    }

    @Test
    void listenerThrowingTerminationSignalFromOnExitYieldsTerminatedStatus() {
        NodeListener listener = new NodeListener() {
            @Override
            public void onExit(String nodeName, Object state) {
                if ("b".equals(nodeName)) {
                    throw new TerminationSignalException("done at b");
                }
            }
        };

        Graph<CounterState> graph = Graph.<CounterState>builder()
                .node("a", (s, ctx) -> s.inc())
                .node("b", (s, ctx) -> s.inc())
                .node("c", (s, ctx) -> s.inc())
                .entry("a")
                .edge("a", "b")
                .edge("b", "c")
                .terminal("c")
                .listener(listener)
                .build();

        ExecutionResult<CounterState> result = graph.run(new CounterState(0));

        assertThat(result.status()).isEqualTo(Status.TERMINATED);
        assertThat(result.error()).isNull();
        assertThat(result.path()).containsExactly("a", "b");
        assertThat(result.finalState().count()).isEqualTo(2);
    }

    @Test
    void terminationSignalFromFirstNodeStopsBeforeAnyOtherRuns() {
        AtomicInteger reached = new AtomicInteger();
        NodeListener listener = new NodeListener() {
            @Override
            public void onExit(String nodeName, Object state) {
                throw new TerminationSignalException("immediate");
            }
        };

        Graph<CounterState> graph = Graph.<CounterState>builder()
                .node("a", (s, ctx) -> s.inc())
                .node("b", (s, ctx) -> { reached.incrementAndGet(); return s.inc(); })
                .entry("a")
                .edge("a", "b")
                .terminal("b")
                .listener(listener)
                .build();

        ExecutionResult<CounterState> result = graph.run(new CounterState(0));

        assertThat(result.status()).isEqualTo(Status.TERMINATED);
        assertThat(result.path()).containsExactly("a");
        assertThat(reached.get()).isZero();
    }

    @Test
    void terminationSignalCarriesMessage() {
        TerminationSignalException ex = new TerminationSignalException("budget hit");
        assertThat(ex.getMessage()).isEqualTo("budget hit");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void terminatedStatusEnumExists() {
        assertThat(Status.valueOf("TERMINATED")).isEqualTo(Status.TERMINATED);
    }
}
