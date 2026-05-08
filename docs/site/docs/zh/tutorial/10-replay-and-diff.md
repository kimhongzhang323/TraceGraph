---
title: 回放与差异比较
---

# 回放与差异比较

> AI 翻译草稿 — 请校对。

介绍 `TraceRecorder`、`TraceStore` 的用途，以及 `Replayer` 与 `TraceDiff` 的基本用法：加载执行追踪、逐步检查、对比两次执行的差异。

示例：使用 `JsonFileTraceStore` 保存并加载追踪

```java
TraceStore store = JsonFileTraceStore.of(Paths.get("/var/traces"), OrderState.class);
TraceRecorder recorder = new RecordingTraceRecorder(store);
// 在 Graph.Builder 中注入 recorder

// 加载追踪并回放
ExecutionTrace<OrderState> t = store.load(executionId, OrderState.class);
ReplayRunner.of(parent, graph).reRunFrom(stepIndex);
```

使用 `TraceDiff.between(left, right)` 可以得到最长公共前缀与分歧点，方便把两个执行的差异可视化为“相同到第 N 步，然后不同”。

练习：运行两次相同的图，一次使用 `MockLlmClient` 返回固定答案，另一次引入少量随机扰动，使用 `TraceDiff` 比较差异。
