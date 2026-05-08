# TraceGraph :: Benchmarks

## Overview
The `tracegraph-bench` module is dedicated to performance testing and benchmarking the core TraceGraph components using the Java Microbenchmark Harness (JMH). It is crucial for ensuring that the graph execution remains highly performant and allocation-efficient.

## Benchmark Flow

```mermaid
flowchart LR
    A[TraceGraph Core] --> B(JMH Runner)
    C[TraceGraph Connectors] --> B
    B --> D[Performance Metrics]
    B --> E[Throughput Stats]
```

## Features
- High-precision microbenchmarks
- Profiling of core graph execution pathways
- JMH annotation processor integration
