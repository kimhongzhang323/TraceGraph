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
 * Measures overhead of subgraphs nested to depth 1 / 4 / 8.
 * Each level wraps the inner graph as a subgraph node, with a passthrough entry and terminal.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DeepNestingBenchmark {

    @Param({"1", "4", "8"})
    public int depth;

    private Graph<String> graph;

    @Setup
    public void setup() {
        graph = buildNested(depth);
    }

    private static Graph<String> buildNested(int d) {
        if (d == 1) {
            return Graph.<String>builder()
                    .node("leaf", (s, ctx) -> s)
                    .entry("leaf")
                    .terminal("leaf")
                    .build();
        }
        Graph<String> inner = buildNested(d - 1);
        return Graph.<String>builder()
                .node("entry" + d, (s, ctx) -> s)
                .subgraph("inner" + d, inner)
                .node("exit" + d, (s, ctx) -> s)
                .edge("entry" + d, "inner" + d)
                .edge("inner" + d, "exit" + d)
                .entry("entry" + d)
                .terminal("exit" + d)
                .build();
    }

    @Benchmark
    public Object run() {
        return graph.run("seed");
    }
}
