---
title: 回放与差异比较
---

# 10 — 回放与差异比较

回放功能允许您从任意步骤重新执行已保存的追踪记录，是调试、提示词迭代和回归测试的重要手段。差异比较则用于在结构上对比两条追踪记录。

## 记录追踪

在图构建时注入 `TraceRecorder` 和 `TraceStore`：

```java
InMemoryTraceStore<RagState> traceStore = new InMemoryTraceStore<>();
TraceRecorder<RagState> recorder = new RecordingTraceRecorder<>(traceStore);

Graph<RagState> graph = Graph.<RagState>builder()
    // ... 节点与边 ...
    .traceRecorder(recorder)
    .build();

ExecutionResult<RagState> result = graph.run(RagState.of("What is the capital of France?"));
String executionId = result.executionId();
```

## 检查追踪记录

```java
ExecutionTrace<RagState> trace = traceStore.load(executionId).orElseThrow();

for (TraceStep<RagState> step : trace.steps()) {
    System.out.printf("%-12s  attempts=%d  before=%s%n",
        step.nodeName(), step.attempts(), step.before());
}
```

每个 `TraceStep` 记录了 `nodeName`（节点名称）、`before`（前状态）、`after`（后状态）、`attempts`（重试次数）以及每步的 `usage`（LLM 节点的提示词和补全令牌数）。

## 从某步骤回放

`ReplayRunner` 可从指定步骤索引开始，对同一图（或修改后的图）进行重新执行：

```java
Graph<RagState> improvedGraph = // ... 使用了更好提示词的图 ...

ReplayRunner<RagState> runner = ReplayRunner.of(trace, improvedGraph);
ExecutionResult<RagState> forked = runner.reRunFrom(1);  // 从步骤索引 1 开始重新运行

System.out.println(forked.executionId());          // 新的 UUID
System.out.println(forked.forkedFromExecutionId()); // 原始 UUID
System.out.println(forked.forkedFromStepIndex());   // 1
```

传入 `stepIndex = -1` 可使用原始种子状态从入口节点开始回放。新的 `ExecutionTrace` 携带 `forkedFromExecutionId` 和 `forkedFromStepIndex` 用于溯源。

## 提供替代种子状态

通过传入第二个参数来覆盖回放的种子状态：

```java
RagState alternativeSeed = trace.steps().get(1).before().withQuery("Different question?");
ExecutionResult<RagState> forked = runner.reRunFrom(1, alternativeSeed);
```

## 比较两条追踪记录

`TraceDiff.between(left, right)` 逐步遍历两条追踪记录，找出最长公共前缀：

```java
ExecutionTrace<RagState> original = traceStore.load(executionId).orElseThrow();
ExecutionTrace<RagState> forked   = traceStore.load(forked.executionId()).orElseThrow();

TraceDiff<RagState> diff = TraceDiff.between(original, forked);

System.out.println("分歧步骤: " + diff.divergenceIndex());
System.out.println("最终状态相同: " + diff.sameFinalState());
System.out.println("完全一致: " + diff.identical());
```

`diff.leftRemainder()` 和 `diff.rightRemainder()` 分别包含各自追踪记录在分歧点之后的步骤。

## 使用 JsonFileTraceStore 持久化追踪

```java
TraceStore<RagState> fileStore = JsonFileTraceStore.of(
    Path.of("/var/tracegraph/traces"),
    RagState.class
);
```

文件以原子方式写入（`*.tmp` + `ATOMIC_MOVE`）。Throwable 的序列化是有损的——只保留类名和消息。

## 要点总结

- `RecordingTraceRecorder` + `InMemoryTraceStore`（或 `JsonFileTraceStore`）捕获完整的逐步历史记录。
- `ReplayRunner.of(trace, graph).reRunFrom(stepIndex)` 从任意步骤以新的执行 ID 重新执行。
- 派生追踪记录携带 `forkedFromExecutionId` + `forkedFromStepIndex` 用于溯源。
- `TraceDiff.between(a, b)` 找出首个分歧点并比较最终状态。
