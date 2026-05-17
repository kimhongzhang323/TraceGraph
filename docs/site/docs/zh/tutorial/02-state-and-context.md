---
title: 状态与上下文
---

# 02 — 状态与上下文

每个节点接收两个参数：当前状态和一个 `Context` 对象。本教程介绍 `Context` 提供的功能，以及如何为实际工作负载设计状态。

## 演进状态 Record

真实的流水线在推进过程中会不断积累数据。为所需的执行元数据添加字段：

```java
record PipelineState(
    String input,
    String cleaned,
    String result,
    List<String> log
) {
    static PipelineState of(String input) {
        return new PipelineState(input, null, null, List.of());
    }

    PipelineState withLog(String entry) {
        var next = new ArrayList<>(log);
        next.add(entry);
        return new PipelineState(input, cleaned, result, List.copyOf(next));
    }
}
```

## 使用 Context

`Context` 会传递给每个节点，携带执行作用域内的元数据。

```java
Node<PipelineState> clean = (state, ctx) -> {
    String cleaned = state.input().strip().toLowerCase();
    String logEntry = "[%s] cleaned".formatted(ctx.executionId());
    return new PipelineState(state.input(), cleaned, null, state.log())
        .withLog(logEntry);
};
```

### `ctx.executionId()`

当前运行的稳定 UUID。在记录日志或跨服务关联追踪记录时使用。

### `ctx.idempotencyKey()`

由 `executionId` + 节点名称 + 尝试次数派生的确定性键。将其传给上游 HTTP 或 JDBC 调用，可在不重复应用副作用的情况下使重试变得安全。

```java
Node<PipelineState> callApi = (state, ctx) -> {
    String result = externalService.call(state.cleaned(), ctx.idempotencyKey());
    return new PipelineState(state.input(), state.cleaned(), result, state.log());
};
```

### `ctx.memory()`

访问 `MemoryStore` 以实现跨执行持久化。详见[教程 06](06-memory.md)。

### `ctx.reportUsage(promptTokens, completionTokens)`

用于 LLM 节点向监听器上报令牌消耗量。详见[教程 07](07-llm-and-tools.md)。

## 状态组合 vs. 泛型结果类型

TraceGraph 使用单一类型参数 `<S>`。不要使用 `Node<S, R>`，而是将子结果折叠回状态中：

```java
// 正确 — 子结果作为状态 Record 的字段
record PipelineState(String input, String cleaned, String result, ...) {}

// 避免 — 两个类型参数会破坏 Builder 类型推断并使恢复变得复杂
// Node<S, R>  ← 这不是 TraceGraph 的工作方式
```

## 要点总结

- `ctx.executionId()` 提供每次运行的稳定 UUID；用于日志记录和关联。
- `ctx.idempotencyKey()` 按尝试次数划分作用域；将其传给外部调用以支持安全重试。
- 状态是值类型——节点返回新状态，永远不要修改接收到的状态对象。
- 将子结果折叠到状态 Record 的字段中，而不是添加类型参数。
