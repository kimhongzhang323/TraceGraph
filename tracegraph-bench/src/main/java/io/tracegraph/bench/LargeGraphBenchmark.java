package io.tracegraph.bench;

import io.tracegraph.core.Graph;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures per-run overhead vs. graph size for sequential linear graphs of N nodes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class LargeGraphBenchmark {

    @Param({"10", "100", "1000"})
    public int n;

    private Graph<Integer> graph;

    @Setup
    public void setup() {
        Graph.Builder<Integer> builder = Graph.<Integer>builder();
        for (int i = 0; i < n; i++) {
            int idx = i;
            builder.node("node" + idx, (s, ctx) -> s + 1);
        }
        for (int i = 0; i < n - 1; i++) {
            builder.edge("node" + i, "node" + (i + 1));
        }
        builder.entry("node0")
               .terminal("node" + (n - 1))
               .maxSteps(n + 10);
        graph = builder.build();
    }

    @Benchmark
    public Object run() {
        return graph.run(0);
    }
}
