---
title: 重试与故障
---

# 03 — 重试与故障

生产环境中的节点会调用存在瞬时故障的外部服务。TraceGraph 的重试系统属于图定义的一部分——而非运行时配置——因此重试策略可复现、可版本化，并在追踪记录中清晰可见。

## RetryPolicy

使用 `RetryPolicy.of(maxAttempts, backoffStrategy)` 创建策略：

```java
RetryPolicy fixed       = RetryPolicy.of(3, BackoffStrategy.fixed(200));
RetryPolicy exponential = RetryPolicy.of(5, BackoffStrategy.exponential(100, 10_000));
```

`BackoffStrategy.fixed(ms)` 在每次重试之间等待固定毫秒数。`BackoffStrategy.exponential(baseMs, maxMs)` 每次将延迟翻倍，上限为 `maxMs`。

## 将策略附加到节点

将策略作为第三个参数传给 `.node(...)`：

```java
RetryPolicy threeAttempts = RetryPolicy.of(3, BackoffStrategy.exponential(200, 5_000));

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("callApi", callApiNode, threeAttempts)
    .node("process", processNode)
    .edge("callApi", "process")
    .entry("callApi")
    .terminal("process")
    .build();
```

## 默认重试策略

为所有未设置自己策略的节点应用默认策略：

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("callApi", callApiNode, RetryPolicy.of(5, BackoffStrategy.exponential(100, 8_000)))
    .node("process", processNode)
    .defaultRetryPolicy(RetryPolicy.of(2, BackoffStrategy.fixed(500)))
    .entry("callApi")
    .terminal("process")
    .build();
```

节点级策略优先于默认策略。`process` 节点会获得两次重试机会（固定 500ms 退避）；`callApi` 则使用自己的五次指数退避策略。

## 重试时的幂等性

使用 `ctx.idempotencyKey()` 避免重复应用副作用。该键在同一次执行中同一节点的所有重试尝试间保持稳定：

```java
Node<PipelineState> callApiNode = (state, ctx) -> {
    String response = httpClient.post(
        "/enrich",
        state.cleaned(),
        Map.of("Idempotency-Key", ctx.idempotencyKey())
    );
    return state.withResult(response);
};
```

## 重试不涵盖的场景

- `Error` 和 `InterruptedException` 总是短路重试循环——它们会立即传播。
- 节点耗尽所有重试次数后会抛出 `NodeExecutionException`，将 `ExecutionResult.status` 设置为 `FAILED`。

```java
ExecutionResult<PipelineState> result = graph.run(PipelineState.of("hello"));
if (result.status() == Status.FAILED) {
    result.failureCause().ifPresent(Throwable::printStackTrace);
}
```

## 要点总结

- `RetryPolicy` 在图定义时确定，而非运行时配置——在各环境中可复现。
- `BackoffStrategy.fixed` 和 `.exponential` 覆盖了两种最常见的退避模式。
- 节点级策略会覆盖 `defaultRetryPolicy`。
- 使用 `ctx.idempotencyKey()` 使外部调用在重试时具有幂等性。
- `Error` 和 `InterruptedException` 完全绕过重试机制。
