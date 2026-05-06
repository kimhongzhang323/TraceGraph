package io.tracegraph.bench;

import io.tracegraph.core.Graph;
import io.tracegraph.core.RetryPolicy;
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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures retry and listener overhead when a node fails N-1 times before succeeding.
 * The AtomicInteger counter is reset at the start of each benchmark invocation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RetryStormBenchmark {

    @Param({"1", "5", "20"})
    public int failCount;

    private Graph<String> graph;
    private final AtomicInteger attempts = new AtomicInteger(0);

    @Setup
    public void setup() {
        graph = Graph.<String>builder()
                .node("work", (s, ctx) -> {
                    if (attempts.getAndIncrement() < failCount - 1) {
                        throw new RuntimeException("simulated failure");
                    }
                    return s + "done";
                })
                .entry("work")
                .terminal("work")
                .defaultRetryPolicy(RetryPolicy.fixed(25, Duration.ZERO))
                .build();
    }

    @Benchmark
    public Object run() {
        attempts.set(0);
        return graph.run("seed");
    }
}
