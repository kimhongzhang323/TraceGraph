---
title: 边与路由
---

# 边与路由



# 边与路由（Edge predicates、`goTo`、`sendAll`）

边（Edge）带有谓词（predicate）用于决定在节点退出后是否沿该边继续。图的边解析按声明顺序评估谓词，首个匹配的边被选中并作为下一跳；如果没有匹配边，执行到达终止。

1) 边的谓词（Edge predicates）

- 谓词函数签名：`Predicate<S>`，只能基于当前状态 `S` 决定真/假。必须是纯函数，以便重放/恢复期间重新评估不会出现不一致。
- 常见用途：状态字段检查、阈值、feature flag、以及基于先前步骤结果的路由。

示例：

```java
.edge("validate", "charge", s -> s.isValid())
.edge("validate", "reject", s -> !s.isValid())
```

2) `goTo`（显式跳转）

路由节点可以返回 `NodeResult.goTo(name, newState)` 来显式选择目标节点，而绕过边的谓词解析。`goTo` 适用于动态决定目标但不想把逻辑表达为一堆边谓词的场景。

示例：

```java
return NodeResult.goTo("specialFlow", stateWithOverrides);
```

注意：`goTo` 的目标必须存在于图中，否则会抛出 `NodeExecutionException`。

3) `sendAll` / 动态分发

`sendAll` 允许在运行时生成 N 个目标与对应荷载（`Send<S>`），并并发执行这些目标，随后用 `Merger<S>` 合并分支结果。

使用场景：批量并行处理、fan-out to multiple workers（外部 API 并行调用）或对多条路径进行并行尝试。

示例：

```java
List<Send<OrderState>> sends = items.stream()
	.map(item -> new Send<>("processItem", state.withItem(item)))
	.toList();
return NodeResult.sendAll(sends, Merger.of((a,b)->a.merge(b)), state);
```

合并（Merger）契约：按声明顺序合并分支结果；当合并逻辑失败或分支异常时，合并器应明确定义失败策略（抛异常或返回备选状态）。

4) 错误语义与优先级

- 首先匹配的边优先；如果使用 `goTo` 则绕过谓词顺序。
- 在 `sendAll` 场景中，如果任一分支失败，默认行为是将失败上抛并使整体节点失败（可通过自定义 merger 捕获并处理部分失败）。

5) 最佳实践

- 对复杂路由用单元测试覆盖所有分支条件。
- 把 routing logic 作为纯函数提取到独立的类以便重用与测试。
- 在 `sendAll` 场景中限制最大并发数（保护外部系统与自实例资源）。

练习：实现一个路由节点，根据订单金额把订单发送到不同的处理队列（`highValue`, `normal`, `manualReview`），并用 `sendAll` 并发地为每个相关子订单触发 `processItem` 节点，最后合并为总状态。
