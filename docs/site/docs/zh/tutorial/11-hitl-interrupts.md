---
title: HITL 中断
---

# 11 — HITL 中断

人机交互（Human-in-the-Loop，HITL）暂停允许运维人员在执行继续之前检查或批准图的状态。TraceGraph 将此功能作为一等公民的中断机制实现——无需轮询，无需 sleep 循环。

## interruptBefore 与 interruptAfter

在图构建时声明中断点：

```java
Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("draft",   draftNode)
    .node("review",  reviewNode)
    .node("publish", publishNode)
    .edge("draft",   "review")
    .edge("review",  "publish")
    .entry("draft")
    .terminal("publish")
    .checkpointStore(checkpointStore)
    .interruptBefore("publish")   // 在发布前暂停；运维人员必须批准
    .build();
```

`interruptBefore("publish")` 会在 `publish` 即将运行之前暂停执行器并写入检查点。`interruptAfter("review")` 则在 `review` 完成并写入检查点之后暂停。

## 运行到中断点

```java
ExecutionResult<ApprovalState> result = graph.run(ApprovalState.of("Draft content..."));
System.out.println(result.status());      // INTERRUPTED
System.out.println(result.executionId()); // 保存此 ID 以便后续恢复
```

执行器到达 `interruptBefore` 点时，会设置 `Status.INTERRUPTED` 并返回。此时状态已保存在检查点存储中。

## 恢复前检查状态

加载检查点（或追踪记录）以查看待处理状态：

```java
ApprovalState pending = checkpointStore.load(executionId)
    .map(Checkpoint::state)
    .orElseThrow();

System.out.println(pending.draft()); // 运维人员审查此内容
```

## 批准后恢复

```java
// 运维人员已批准 — 恢复执行
ExecutionResult<ApprovalState> completed = graph.resume(executionId);
System.out.println(completed.status()); // COMPLETED
```

`graph.resume(id)` 从检查点继续执行。`publish` 节点将正常运行。

## Spring Boot REST 端点

使用 Spring Boot Starter 时，中断/恢复流程开箱即用地提供了 REST 端点：

```
# 检查某次运行是否已中断
GET /tracegraph/traces/{id}        → { "status": "INTERRUPTED", ... }

# 恢复
POST /tracegraph/traces/{id}/resume
→ 200 { "status": "COMPLETED" }   （或 404 表示未知，409 表示状态不是 INTERRUPTED）
```

## 恢复前修改状态

如果运维人员希望在继续前修改状态，可以先加载、修改并重新保存检查点，然后再调用 `resume`：

```java
Checkpoint<ApprovalState> cp = checkpointStore.load(executionId).orElseThrow();
ApprovalState approved = cp.state().withApproverNote("LGTM — proceed.");
checkpointStore.save(cp.withState(approved));

graph.resume(executionId);
```

## 要点总结

- `interruptBefore(name)` 在指定节点之前暂停；`interruptAfter(name)` 在其之后暂停。
- 中断的运行返回 `Status.INTERRUPTED` 并写入检查点——不会抛出异常。
- `graph.resume(id)` 从检查点继续执行；被中断的节点在恢复时正常运行。
- 不支持 `parallel(...)` 内部的分支级中断。
- Spring Boot Starter 暴露了 `POST /tracegraph/traces/{id}/resume`，用于基于 REST 的审批流程。
