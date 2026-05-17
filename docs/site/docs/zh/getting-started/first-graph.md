---
title: 第一个图
---

# 第一个图

本页将引导您构建并运行最简单的 TraceGraph 程序——一个对字符串进行转换的两节点流水线。

## 定义状态

状态可以是您自己拥有的任意 Java 类型。推荐使用 Record，因为它默认是不可变的。

```java
record PipelineState(String input, String output) {}
```

## 定义节点

`Node<S>` 是一个函数式接口：接收当前状态和一个 `Context`，返回下一个状态。

```java
Node<PipelineState> normalize = (state, ctx) ->
    new PipelineState(state.input().strip().toLowerCase(), null);

Node<PipelineState> greet = (state, ctx) ->
    new PipelineState(state.input(), "Hello, " + state.input() + "!");
```

## 构建图

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("normalize", normalize)
    .node("greet", greet)
    .edge("normalize", "greet")
    .entry("normalize")
    .terminal("greet")
    .build();
```

`entry` 标记第一个接收种子状态的节点；`terminal` 标记执行在其之后停止的节点。

## 运行图

```java
PipelineState initial = new PipelineState("  World  ", null);
ExecutionResult<PipelineState> result = graph.run(initial);

System.out.println(result.finalState().output()); // Hello, world!
System.out.println(result.status());              // COMPLETED
System.out.println(result.executionId());         // UUID
```

`Graph.run` 是同步的，会阻塞直到图完成或失败。返回的 `ExecutionResult` 是不可变 Record，可以安全地自由传递。

## 要点总结

- 状态是普通 Java 类型；Record 最为适合，因为它是不可变的。
- 节点是纯函数：`(S, Context) -> S`，不对状态对象产生副作用。
- `entry` 和 `terminal` 是必填项——没有它们，Builder 会抛出 `GraphValidationException`。
- `graph.run(seed)` 返回包含 `finalState`、`status` 和 `executionId` 的 `ExecutionResult<S>`。
