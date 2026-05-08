# tracegraph-core（核心模块 — 详细指南）

`tracegraph-core` 是 TraceGraph 的核心库，提供构建与执行类型化执行图（typed execution graphs）的基本抽象与同步执行语义。它保持轻量无额外运行时依赖，便于在应用中直接引用或被其他模块扩展（如 runtime、observability、memory）。

目标读者：想深入了解框架 API 的开发者、需要在业务代码中构建图式工作流的工程师、以及要为框架编写单元测试的同学。

目录（本页）：简介 → 关键概念 → 主要类与入口 → 代码示例 → 设计注意事项 → 本地开发与测试

核心概念与术语
- Graph<S>: 有向执行图，按命名节点组织；通过 `Graph.builder()` 构建。
- Node: 图的基本单元。节点接收当前状态 `S` 和 `Context`，返回新的状态或路由指令。
- Edge: 描述从一个节点到另一个节点的条件或无条件转移（可带谓词）。
- ExecutionResult<S>: 表示一次执行的结果，包含 `executionId`、最终状态、路径、状态码与错误信息。
- Context: 每次节点执行时传入的运行时上下文，提供 `memory()`、`idempotencyKey()`、`reportUsage()` 等工具。

主要 API 入口（先看这些文件）
- `io.tracegraph.core.Graph` — 构建器与运行入口（`builder()`, `run(seed)`, `resume(id)`, `stream()` 等）。
- `io.tracegraph.core.Node` — 节点函数接口（同步、异步、路由、并行分支的签名）。
- `io.tracegraph.core.ExecutionResult` — 执行结果类型。
- `io.tracegraph.core.Context` — 执行上下文。
- `io.tracegraph.core.retry.RetryPolicy` — 节点重试策略（fixed / exponential）
- `io.tracegraph.core.spi.TraceRecorder` — 回放/记录钩子接口（observability 模块实现它）。

快速示例（逐行解释）

```java
// 1) 定义不可变状态
record OrderState(String id, boolean valid, boolean charged, boolean shipped) {}

// 2) 构建图
Graph<OrderState> graph = Graph.<OrderState>builder()
	.node("validate", (state, ctx) -> state.withValid(true))
	.node("charge", (state, ctx) -> state.withCharged(true))
	.entry("validate")
	.edge("validate", "charge", OrderState::valid)
	.terminal("charge")
	.build();

// 3) 运行
ExecutionResult<OrderState> r = graph.run(new OrderState("o-1", false, false, false));
```

示例说明：
- `node(...)` 注册命名节点；节点实现只改变状态并返回新状态。
- `edge(from, to, predicate)` 用谓词控制是否沿某条边前进；未提供谓词的边始终成立。
- `entry(...)` 声明入口节点，`terminal(...)` 声明终止节点。

节点类型（更多用例）
- 同步节点：返回新的 `S`。
- 异步节点：返回 `CompletableFuture<S>`，用于 I/O 或异步计算。
- 路由节点（RoutingNode）：返回 `NodeResult.goTo(name, state)` 来跳转到任意命名节点。
- 并行分支（parallel）：声明多条分支并行执行，使用合并器（merger）将分支结果合并回主状态。

重试、检查点与幂等性（要点）
- `RetryPolicy` 可以附加到单个节点或设置为图的默认策略。注意 `Error` 与 `InterruptedException` 不会重试。
- 如果图配置了 `CheckpointStore`，检查点在节点成功退出后写入。恢复时会从 `lastCompletedNode` 继续并重新评估其出边。
- 因为恢复可能导致节点重复执行，节点实现应当是幂等的，或使用 `Context.idempotencyKey()` 做服务端去重。

可观测性与回放（钩子）
- `NodeListener` SPI（可组合）用于将节点进入/退出/异常/重试等事件上报给观测系统（例如 OpenTelemetry）。
- `TraceRecorder` 用于把每一步写入 `TraceStore`（InMemory / JsonFile / JDBC），便于后续回放（Replay）与差异对比（TraceDiff）。

测试与开发注意事项
- 构建：`mvn -B -ntp verify`。
- 单模块测试：`mvn -pl tracegraph-core test`。
- 保持 `Graph`/节点的小粒度，便于编写针对节点的单元测试（mock `Context` / `MemoryStore`）。
- 避免在节点中使用线程局部可变状态；推荐使用不可变记录返回新状态。

学习建议（逐步掌握）
1. 先阅读 `Graph` 类和 builder DSL，写一个最小图并运行（examples/quickstart）。
2. 学习 `Node` 的不同变体（同步/异步/parallel/routing）。
3. 接入 `RecordingTraceRecorder` 与 `InMemoryTraceStore`，运行并使用 `Replayer` 回放。理解检查点与恢复语义。
4. 阅读 `tracegraph-runtime` 和 `tracegraph-observability` 的 README（此外还有示例），了解如何在生产中运维图执行。

文件位置提示
- 主要源码：`tracegraph-core/src/main/java/...`。
- 测试：`tracegraph-core/src/test/java/...`。
- 示例：`examples/quickstart`。

如果你希望，我可以把 `tracegraph-core` README 中的每个章节进一步扩展为完整教程（逐节示例与练习题），或者接着把 tutorial 01/02 完整翻译并扩展为教学内容。你要我接着做哪一项？

