---
title: HITL 中断
---

# HITL 中断（Human-in-the-loop）


# 人机交互（HITL）与中断

HITL（Human-In-The-Loop）在需要人工确认或审计的关键点非常有用。TraceGraph 提供 `Builder.interruptBefore(...)` / `interruptAfter(...)` 来在指定节点处暂停执行并写入带 `interruptPending=true` 的检查点。

典型流程：

1. 执行到 `approval` 节点，图写入带 `interruptPending=true` 的检查点并返回 `Status.INTERRUPTED`。
2. 运维或产品人员通过控制台或 API 批准执行（触发 resume）。
3. `Graph.resume(executionId)` 继续执行，图将从保存的中断点继续。

练习：在 `examples/hitl-approval` 中启用 `interruptBefore("ship")`，用本地脚本模拟批准后恢复执行。
