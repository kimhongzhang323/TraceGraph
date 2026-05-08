---
title: 节点与边
---

# 节点与边



讲解节点定义、节点函数签名、路由节点（goTo）与边的谓词（edge predicates）。示例展示如何以命名节点构成执行路径。
 
## 目标

本章目标：
- 理解 `Node` 的签名与常见变体（同步、异步、路由）。
- 理解 `Edge`（边）如何根据状态决定流向。 
- 用代码构建并运行一个最小图（Graph），并观察执行路径。 
- 提供练习以加深理解。

## 1. Node（节点）概念

节点是执行图的基本单元。每个节点接收当前状态 `S` 和运行时 `Context`，并返回新的状态或特殊的路由指令。常见 Java 签名如下：

```java
// 同步节点：立即返回新状态
BiFunction<S, Context, S> syncNode = (state, ctx) -> { /* 返回新的 state */ };

// 异步节点：返回 CompletableFuture<S>
BiFunction<S, Context, CompletableFuture<S>> asyncNode = (state, ctx) -> CompletableFuture.supplyAsync(() -> { /* 异步计算 */ });

// 路由节点：可以返回 goTo 指令跳转到指定节点
RoutingNode<S> routing = (state, ctx) -> NodeResult.goTo("nextNode", state);
```

实现要点：
- 节点应当尽量无副作用（pure）或以幂等方式调用外部系统（因为恢复时可能重试）。
- 避免在节点中使用线程局部的可变状态；优先返回不可变 `record` 的新实例。

## 2. Edge（边）与谓词

边连接两个命名节点，可携带谓词（predicate）决定是否沿该边前进。谓词是 `Predicate<S>`，基于当前状态判断：

```java
// 无条件边
graph.edge("validate", "charge");

// 条件边：只有状态满足 predicate 时才会走这条边
graph.edge("validate", "charge", s -> s.valid());
```

边解析顺序与图的声明顺序有关；当多个边都成立时，按声明顺序评估，直到命中一条合适的边（实现可能有具体语义，参见 API 文档）。

## 3. 简单示例：验证—收款—发货流程

下面给出一个最小可运行示例，使用不可变状态 `OrderState`（Java record）：

```java
record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
	OrderState withValid(boolean v) { return new OrderState(id, v, charged, shipped); }
	OrderState withCharged(boolean c) { return new OrderState(id, valid, c, shipped); }
	OrderState withShipped(boolean s) { return new OrderState(id, valid, charged, s); }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
	.node("validate", (state, ctx) -> state.withValid(true))
	.node("charge", (state, ctx) -> state.withCharged(true))
	.node("ship", (state, ctx) -> state.withShipped(true))
	.entry("validate")
	.edge("validate", "charge", OrderState::valid)
	.edge("charge", "ship")
	.terminal("ship")
	.build();

ExecutionResult<OrderState> res = graph.run(new OrderState("o1", false, false, false));
System.out.println(res); // 查看最终状态与执行路径
```

说明：
- `validate` 节点执行后更新 `valid` 字段；`edge("validate","charge", OrderState::valid)` 表示只有在 `valid==true` 时才会进入 `charge` 节点。
- `terminal("ship")` 将 `ship` 标记为终止节点，图在达到终止节点后结束并返回 `ExecutionResult`。

## 4. 路由节点示例（动态跳转）

路由节点允许在运行时决定下一个命名节点：

```java
graph.node("router", (state, ctx) -> {
	if (state.valid()) return NodeResult.goTo("charge", state);
	else return NodeResult.goTo("reject", state);
});
```

`goTo` 可以跳过常规的边解析逻辑，直接指定下一步节点。注意：跳转目标必须存在，否则会抛出 `NodeExecutionException`。

## 5. 异常与重试

如果节点抛出异常，执行器会根据节点或图的 `RetryPolicy` 决定是否重试。常见提示：
- 对于外部 I/O（HTTP、DB、LLM 调用）使用有界重试与指数退避。
- `Error` 与 `InterruptedException` 不会被重试。

## 6. 练习（练习有助于巩固）

练习 1 — 条件流：扩展上面的订单流程，使得当订单未通过验证时进入 `reject` 节点，返回 `ExecutionResult` 中标记为 `REJECTED`。

练习 2 — 异步节点：把 `charge` 节点改为异步模拟外部支付调用（`CompletableFuture.supplyAsync(...)`），并观察 `ExecutionResult` 是否正确返回最终状态。

练习 3 — 路由结合 sendAll：实现一个节点，它基于当前订单生成多个发货目标（不同的仓库），使用 `sendAll` 并合并结果为最终 `shipped` 标记。

## 7. 小结

本章你学会了节点签名、边的谓词、路由节点与一个最小的订单示例。下一章将深入讨论状态模型与 `Context` 的使用（例如 `MemoryStore`、`idempotencyKey()`、以及如何在节点里记录 LLM token 使用量）。

如果你想，我可以把练习 1–3 的参考实现补到仓库 `examples/quickstart` 并在 `docs` 中添加可运行指南。要我继续吗？
