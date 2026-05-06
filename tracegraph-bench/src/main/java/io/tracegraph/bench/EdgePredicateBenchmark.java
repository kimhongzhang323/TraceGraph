package io.tracegraph.bench;

import io.tracegraph.core.Graph;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures edge predicate evaluation overhead with 20 conditional edges fanning out from a
 * single dispatch node. Only the last edge's predicate will be true, forcing all 19 prior
 * predicates to be evaluated first.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class EdgePredicateBenchmark {

    private static final int EDGE_COUNT = 20;

    private Graph<Integer> graph;

    @Setup
    public void setup() {
        Graph.Builder<Integer> builder = Graph.<Integer>builder();
        builder.node("dispatch", (s, ctx) -> s);
        for (int i = 0; i < EDGE_COUNT; i++) {
            builder.node("target" + i, (s, ctx) -> s);
            builder.terminal("target" + i);
        }
        // Add conditional edges; only the last target matches value == EDGE_COUNT - 1
        for (int i = 0; i < EDGE_COUNT; i++) {
            int idx = i;
            builder.edge("dispatch", "target" + idx, s -> s == idx);
        }
        builder.entry("dispatch");
        graph = builder.build();
    }

    @Benchmark
    public Object runFirstEdge() {
        // s == 0 → first predicate true, no further evaluation
        return graph.run(0);
    }

    @Benchmark
    public Object runLastEdge() {
        // s == EDGE_COUNT-1 → all 19 prior predicates false, last true
        return graph.run(EDGE_COUNT - 1);
    }
}
