---
title: 并行与发送
---

# 并行与发送


# 并行与发送

> AI 翻译草稿 — 请校对。

`parallel(...)` 用于声明编排时要并行运行的多个分支，合并器（merger）负责将分支结果合并回主状态。`sendAll` 在运行时动态产生发送目标。

并行示例：

```java
graph.node("fanout", (state, ctx) -> NodeResult.sendAll(
	List.of(new Send<>("worker", payload1), new Send<>("worker", payload2)),
	Merger.collect(), state));
```

注意事项：
- 分支间不要共享可变状态。
- 合并器应当是确定性的（按声明顺序合并）。

练习：实现一个 `parallel` 分支，分别对不同数据分片执行处理，并合并为单一结果计数。
