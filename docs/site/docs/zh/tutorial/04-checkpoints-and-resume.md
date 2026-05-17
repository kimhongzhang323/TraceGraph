---
title: 检查点与恢复
---

# 04 — 检查点与恢复

调用 LLM 或外部 API 的长时运行图需要能够在进程重启后继续运行。检查点功能让 TraceGraph 在每个节点之后写入进度，并在下次运行时从最后完成的节点处恢复。

## 检查点工作原理

节点成功退出后、解析出边之前，TraceGraph 会写入一个检查点，记录 `executionId`、`lastCompletedNode` 和当前状态。恢复时，它会加载检查点，重新评估 `lastCompletedNode` 的出边，然后从那里继续执行。

由于出边谓词在恢复时会被重新评估，它们**必须是状态的纯函数**——这是[教程 01](01-nodes-and-edges.md) 中的相同要求，在这里成为正确性的前提条件。

## InMemoryCheckpointStore（开发环境）

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

## JdbcCheckpointStore（生产环境）

`JdbcCheckpointStore` 将检查点存储在单个数据库表中。在启动时调用一次 `initSchema()`：

```java
JdbcCheckpointStore<PipelineState> store =
    new JdbcCheckpointStore<>(dataSource, PipelineState.class);
store.initSchema();

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    // ... 节点与边 ...
    .checkpointStore(store)
    .build();
```

## 运行与恢复

```java
// 首次运行 — 中途崩溃
String executionId = UUID.randomUUID().toString();
ExecutionResult<PipelineState> result = graph.run(PipelineState.of("hello"), executionId);

// 重启后 — 从上次中断处恢复
ExecutionResult<PipelineState> resumed = graph.resume(executionId);
System.out.println(resumed.status()); // COMPLETED
```

`graph.resume(id)` 会加载检查点，跳过已完成的节点，并向前继续执行。

## 至少一次语义

节点在恢复时具有**至少一次**语义。如果崩溃发生在节点执行过程中（已开始但尚未写入检查点），该节点在恢复时会从第 1 次尝试重新运行。请使用 `ctx.idempotencyKey()` 将节点设计为幂等的。

## 要点总结

- 检查点在节点退出后、边解析前写入。
- `InMemoryCheckpointStore` 适用于测试和开发；`JdbcCheckpointStore` 适用于生产环境。
- 节点具有至少一次语义——外部调用请使用 `ctx.idempotencyKey()`。
- 出边谓词必须是纯函数，因为它们在恢复时会被重新评估。
