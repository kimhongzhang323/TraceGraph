# TraceGraph Runtime

`tracegraph-runtime` extends the core graph functionality to support non-blocking execution, parallelism, durability, and fault tolerance.

## Features
- **Async Execution**: Support for `CompletableFuture<S>` inside nodes without blocking carrier threads (designed for Java 21's Virtual Threads).
- **Parallel Nodes**: Execute multiple branches concurrently with deterministic, declaration-order merging.
- **Checkpointing**: Durable checkpoints allow halting and resuming executions explicitly (e.g., interrupt-before/after). Includes `InMemory` and `Jdbc` storage choices.
- **Retries**: Fault tolerance configured precisely through graph-defined retry policies.

## Parallel Execution Sequence

```mermaid
graph TD
    Start[Graph Execution] --> Fork[Parallel Node Trigger]
    Fork --> Branch1[Branch A]
    Fork --> Branch2[Branch B]
    Fork --> Branch3[Branch C]
    Branch1 -.-> Join
    Branch2 -.-> Join
    Branch3 -.-> Join
    Join((Wait For All)) --> Merge[Apply State Merger]
    Merge --> Continue[Continue Graph Traverse]
```