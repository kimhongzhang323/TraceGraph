# TraceGraph :: Benchmarks

## 📖 What are Benchmarks?
Welcome to `tracegraph-bench`! Building a framework that can run a single AI agent is one thing, but building a framework that can handle thousands of concurrent graph executions without exhausting memory is entirely different.

This module contains microbenchmarks for TraceGraph's core components (the execution engine, state management, and routing logic). It uses the **JMH (Java Microbenchmark Harness)** framework to precisely detect throughput bottlenecks, lock contention, and concurrency limits.

### Core Goals
- **Prevent Performance Regressions**: Ensure that newly introduced code (like more complex routing logic) does not degrade the Operations Per Second (OPS) of the graph execution.
- **Allocation Profiling**: Monitor Garbage Collection (GC) pressure and memory allocation rates as State objects are passed between nodes.
- **Concurrency Optimization**: Verify the effectiveness of Virtual Threads and ensure shared components like `MemoryStore` do not become bottlenecks under high load.

## 🏗️ Testing Architecture

Benchmarks do not simulate full HTTP requests; instead, they stress the core execution loop directly at the JVM level.

```mermaid
sequenceDiagram
    participant JMH as JMH Framework
    participant Engine as TraceGraph Engine
    participant Mem as InMemoryStore
    
    JMH->>JMH: Warmup JVM
    loop Every Iteration
        JMH->>Engine: Concurrently submit 10,000 graph executions
        Engine->>Mem: Concurrently Read/Write Checkpoints
        Engine-->>JMH: Return Execution Results
    end
    JMH->>JMH: Generate Throughput and Latency Report
```

## 🚀 How to Run Benchmarks

JMH tests should not be run via normal JUnit runners, as they require JVM warmup and isolation to produce statistically significant results.

### 1. Compile the Project
For the most accurate results, benchmarks should be packaged into an Uber JAR and run independently.
```bash
mvn clean install -DskipTests
cd tracegraph-bench
```

### 2. Execute Benchmarks
Run the packaged benchmark JAR. Depending on the classes tested, this can take anywhere from a few minutes to half an hour.
```bash
java -jar target/benchmarks.jar
```

### 3. Understanding the Output
JMH will output detailed statistics. Key metrics include:
- `Score`: Represents the number of executions per second (for Throughput mode).
- `Error`: The statistical variance of the measurement.
- `GC Allocation Rate`: If the GC profiler is enabled, it shows how many MBs of objects are allocated per second.

### 4. Running a single benchmark class
```bash
java -jar target/benchmarks.jar LinearVsHandwritten
```

---

## Graph.run vs hand-written executor — overhead analysis

`LinearVsHandwrittenBenchmark` is the canonical overhead benchmark. It runs the same
10 integer-increment nodes two ways:

| Variant | What it does |
|---|---|
| `graphRun` | Calls `Graph.run(0)` on a 10-node linear `Graph<Integer>` with no listeners, no checkpoints, and no retry policy. |
| `handwrittenRun` | Iterates a `List<UnaryOperator<Integer>>` (10 identical lambdas) directly — no framework involvement. |

The delta between the two is the raw cost of TraceGraph's synchronous execution engine for
a warm, no-op configuration. The engine's per-run work includes:

1. **UUID generation** — a `UUID.randomUUID()` call to allocate the `executionId`.
2. **Edge map lookup** — one `HashMap.get` per completed node to resolve the next node.
3. **Context allocation** — one `Context` record per node.
4. **No-op SPI dispatch** — `NodeListener.onNodeStart` / `onNodeEnd` invoked on the
   default no-op listener (two virtual calls per node, inlined by JIT after warmup).
5. **`ExecutionResult` record allocation** — wraps the final state, executionId, status,
   and execution path list.

### Representative results (JDK 21, OpenJDK, Apple M2, 1 JMH fork)

> These figures are indicative. Re-run on your target hardware for authoritative numbers.

| Benchmark | Score (µs/op) | ±Error |
|---|---|---|
| `handwrittenRun` | ~0.05 | ±0.001 |
| `graphRun` | ~2–4 | ±0.1 |

**Overhead**: roughly **2–4 µs per `Graph.run` call** compared to a bare function chain.
For AI agent workloads where each node makes an LLM or I/O call (milliseconds to seconds),
this cost is negligible (<0.1% of total latency at 10 ms/node).

The overhead is dominated by `UUID.randomUUID()` (~1 µs) and path-list construction.
Both are one-time-per-run costs that do not scale with graph depth.

### Running the comparison yourself

```bash
mvn clean install -DskipTests
java -jar tracegraph-bench/target/benchmarks.jar LinearVsHandwritten \
     -wi 3 -i 5 -f 1 -tu us -bm avgt
```
