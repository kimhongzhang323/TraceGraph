---
title: 重试与故障
---

# 重试与故障


# 重试与失败

> AI 翻译草稿 — 请校对。

本章重点：

- `RetryPolicy` 的类型（固定、指数退避）
- 节点抛异常时执行器如何决定重试或中止
- 设计幂等节点的最佳实践

配置重试

`RetryPolicy` 可在节点层面或图的默认策略上配置：

```java
RetryPolicy p = RetryPolicy.exponentialBackoff(Duration.ofMillis(200), 3);
graph.node("call", callNode, p);
```

失败与传播

- 如果重试用尽或遇到不可重试错误（`Error`/`InterruptedException`），执行会标记为失败并返回含错误的 `ExecutionResult`。
- 在并行场景下，第一条失败分支通常会短路父执行，具体语义请参见实现文档。

幂等性建议

- 外部请求（支付、HTTP、数据库写）应使用 `Context.idempotencyKey()` 或在服务端实现去重。
- 将副作用限制在合适的位置（例如在路由节点之后）以便更好地控制重试带来的重复副作用。

练习：实现一个模拟 HTTP 调用的节点，使用 `RetryPolicy` 做指数退避，并在 `examples/quickstart` 中观察重试次数。
