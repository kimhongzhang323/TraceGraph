package io.tracegraph.bench;

import io.tracegraph.core.Graph;
import io.tracegraph.core.Merger;
import io.tracegraph.core.Node;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measures parallel fan-out overhead vs. branch count (2 / 16 / 128 branches).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class WideParallelBenchmark {

    @Param({"2", "16", "128"})
    public int branches;

    private Graph<String> graph;

    @Setup
    public void setup() {
        List<Node<String>> branchList = new ArrayList<>(branches);
        for (int i = 0; i < branches; i++) {
            branchList.add((s, ctx) -> s);
        }
        Merger<String> firstWins = (a, b) -> a;

        graph = Graph.<String>builder()
                .node("start", (s, ctx) -> s)
                .parallel("par", branchList, firstWins)
                .node("end", (s, ctx) -> s)
                .edge("start", "par")
                .edge("par", "end")
                .entry("start")
                .terminal("end")
                .build();
    }

    @Benchmark
    public Object run() {
        return graph.run("seed");
    }
}
