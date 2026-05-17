package io.tracegraph.bench;

import io.tracegraph.core.Graph;
import io.tracegraph.core.Node;
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Contrasts {@link Graph#run} against an equivalent hand-written sync loop on a fixed 10-node
 * linear graph. The delta is the raw overhead charged by TraceGraph's execution engine
 * (edge resolution, executionId allocation, Context creation, no-op listener dispatch).
 *
 * <p>Both variants execute identical work: 10 successive integer increments.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class LinearVsHandwrittenBenchmark {

    private static final int NODES = 10;

    private Graph<Integer> graph;
    private List<UnaryOperator<Integer>> chain;

    @Setup
    public void setup() {
        // TraceGraph graph: 10-node linear chain, no retries, no checkpoint, no listeners.
        Graph.Builder<Integer> builder = Graph.<Integer>builder();
        for (int i = 0; i < NODES; i++) {
            builder.node("n" + i, (Node<Integer>) (s, ctx) -> s + 1);
        }
        for (int i = 0; i < NODES - 1; i++) {
            builder.edge("n" + i, "n" + (i + 1));
        }
        builder.entry("n0").terminal("n" + (NODES - 1));
        graph = builder.build();

        // Hand-written equivalent: a plain list of lambdas, iterated directly.
        List<UnaryOperator<Integer>> fns = new java.util.ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) {
            fns.add(s -> s + 1);
        }
        chain = List.copyOf(fns);
    }

    /** TraceGraph {@code Graph.run} on a 10-node linear graph. */
    @Benchmark
    public Object graphRun() {
        return graph.run(0);
    }

    /**
     * Hand-written sync executor: iterates the same 10 lambdas directly with no framework
     * overhead. Serves as the baseline; the difference vs {@link #graphRun} is TraceGraph's cost.
     */
    @Benchmark
    public int handwrittenRun() {
        int state = 0;
        for (UnaryOperator<Integer> fn : chain) {
            state = fn.apply(state);
        }
        return state;
    }
}
