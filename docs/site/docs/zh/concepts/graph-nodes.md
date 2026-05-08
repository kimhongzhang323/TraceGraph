---
title: 图与节点
---

# 图与节点

本节详细解释 `Graph<S>` 与 `Node` 的语义、节点类型（同步、异步、路由、并行）及执行器的驱动流程，并辅以示例与调试技巧。

1) `Graph<S>` 的结构

`Graph` 是由命名节点（String name）与带谓词的边组成的有向图。通过 `Graph.Builder` 定义节点、入口（entry）、终止（terminal）、默认重试策略、监听器与可选的 `TraceRecorder` / `CheckpointStore`。

2) 节点类型回顾

- 同步节点（Sync Node）：接收 `(S, Context)`，直接返回 `S`。
- 异步节点（Async Node）：返回 `CompletableFuture<S>`，适用于阻塞或 I/O 操作。
- 路由节点（RoutingNode）：返回 `NodeResult.goTo(name, state)` 或 `NodeResult.of(state)`，可动态决定下一跳。
- 并行节点（parallel）：声明多条分支并发执行，使用 `Merger` 合并分支返回的状态。

3) 执行器驱动流程（高层次）

1. 从入口节点开始，调用节点函数。
2. 节点返回后，写检查点（如果启用），然后解析出边，按声明顺序评估谓词并选择下一条边。
3. 达到终止节点或没有后续边时，执行结束，返回 `ExecutionResult`。

注意：在恢复路径上，执行器会从 `lastCompletedNode` 重新评估其出边，这要求边的谓词是纯函数（仅基于 `S`）且节点实现幂等或具备去重逻辑。

4) 调试与可观测性建议

- 在开发阶段把 `NodeListener` 设置为打印进入/退出事件，便于观察节点执行顺序。
- 在节点开始与结束处记录 `ctx.reportUsage(...)` 与 `ctx.memory()` 变更，Trace 将显示状态变更。

5) 示例：快速回顾

参见 `docs/tutorial/01-nodes-and-edges` 中的订单示例。把关键节点拆小、每个节点只做一件事，这样更容易测试与回放。

练习：对一个包含 5 个节点的线性图，在 `NodeListener` 中打印进入/退出顺序，并故意在中间节点抛异常观察重试与失败的行为。
