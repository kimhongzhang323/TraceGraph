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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class GraphDispatchBenchmark {

    private Graph<String> linearGraph;
    private Graph<String> parallelGraph;

    @Setup
    public void setup() {
        linearGraph = Graph.<String>builder()
                .node("a", (s, ctx) -> s + "a")
                .node("b", (s, ctx) -> s + "b")
                .node("c", (s, ctx) -> s + "c")
                .edge("a", "b")
                .edge("b", "c")
                .entry("a")
                .terminal("c")
                .build();

        Node<String> branchX = (s, ctx) -> s + "x";
        Node<String> branchY = (s, ctx) -> s + "y";
        Merger<String> concat = (a, b) -> a + b;

        parallelGraph = Graph.<String>builder()
                .node("start", (s, ctx) -> s)
                .parallel("par", List.of(branchX, branchY), concat)
                .node("end", (s, ctx) -> s)
                .edge("start", "par")
                .edge("par", "end")
                .entry("start")
                .terminal("end")
                .build();
    }

    @Benchmark
    public Object linearDispatch() {
        return linearGraph.run("seed");
    }

    @Benchmark
    public Object parallelDispatch() {
        return parallelGraph.run("seed");
    }
}
