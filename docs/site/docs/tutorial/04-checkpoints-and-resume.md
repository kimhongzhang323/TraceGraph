# 04 — Checkpoints & Resume

Long-running graphs that call LLMs or external APIs need the ability to survive process restarts. Checkpointing lets TraceGraph write progress after each node and resume from the last completed node on the next run.

## How checkpointing works

After a node exits successfully and before the outgoing edge is evaluated, TraceGraph writes a checkpoint recording the `executionId`, the `lastCompletedNode`, and the current state. On resume, it loads the checkpoint, re-evaluates the outgoing edges of `lastCompletedNode`, and continues from there.

Because edge predicates are re-evaluated on resume, they **must be pure functions of state** — the same guarantee from [Tutorial 01](01-nodes-and-edges.md) becomes a correctness requirement here.

## InMemoryCheckpointStore (development)

```java
CheckpointStore<PipelineState> store = new InMemoryCheckpointStore<>();

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("fetch",   fetchNode,   RetryPolicy.of(3, BackoffStrategy.exponential(200, 5_000)))
    .node("enrich",  enrichNode)
    .node("persist", persistNode)
    .edge("fetch", "enrich")
    .edge("enrich", "persist")
    .entry("fetch")
    .terminal("persist")
    .checkpointStore(store)
    .build();
```

## JdbcCheckpointStore (production)

`JdbcCheckpointStore` stores checkpoints in a single database table. Call `initSchema()` once at startup:

```java
JdbcCheckpointStore<PipelineState> store =
    new JdbcCheckpointStore<>(dataSource, PipelineState.class);
store.initSchema();

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    // ... nodes and edges ...
    .checkpointStore(store)
    .build();
```

## Running and resuming

```java
// First run — crashes mid-way through
String executionId = UUID.randomUUID().toString();
ExecutionResult<PipelineState> result = graph.run(PipelineState.of("hello"), executionId);

// On restart — resume from where it left off
ExecutionResult<PipelineState> resumed = graph.resume(executionId);
System.out.println(resumed.status()); // COMPLETED
```

`graph.resume(id)` loads the checkpoint, skips completed nodes, and continues forward.

## At-least-once semantics

Nodes are **at-least-once** on resume. If a crash happens while a node is executing (after it started but before the checkpoint was written), that node re-runs from attempt 1 on resume. Design nodes to be idempotent using `ctx.idempotencyKey()`.

## Key takeaways

- Checkpoints are written after node exit, before edge resolution.
- `InMemoryCheckpointStore` is suitable for tests and development; `JdbcCheckpointStore` for production.
- Nodes are at-least-once — use `ctx.idempotencyKey()` for external calls.
- Edge predicates must be pure because they are re-evaluated on resume.
