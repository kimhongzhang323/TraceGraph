# 核心概念

阅读和编写任何 TraceGraph 程序所需的词汇。以下内容都位于 `tracegraph-core`——它没有重型依赖（运行时仅 SLF4J API）。

> 🌐 English: **[[Core Concepts]]**

## 图 (Graph)

`Graph<S>` 是主要的运行时抽象，由**单一类型参数 `<S>`**（状态类型）参数化。不存在 `<S, R>`；子结果用状态组合来表达。

一个图定义了：

- 具名**节点**
- 有向**边**
- 一个**入口**节点
- 一个或多个**终止**节点
- 可选的重试、检查点、追踪、监听器、记忆与执行器行为

`Graph<S>` 在 `build()` 之后**不可变**，可安全跨线程共享；多个 `run` 并行调用永不破坏图状态。`Graph.Builder<S>` **非线程安全**——请在单线程上构建。

```java
Graph<S> graph = Graph.<S>builder()
        .node("a", nodeA)
        .node("b", nodeB)
        .entry("a")
        .edge("a", "b")
        .terminal("b")
        .build();   // 立即校验；输入非法时抛出 GraphValidationException
```

## 节点 (Node)

`Node<S>` 是一个函数式接口：接收当前类型化状态与一个 `Context`，返回下一个状态。**节点返回下一个状态，绝不就地修改**——强烈推荐不可变状态。

| 风格 | 构建器方法 | 返回 |
|---|---|---|
| 同步 | `node(name, fn)` | `S` |
| 异步 | `asyncNode(name, fn)` | `CompletableFuture<S>` |
| 并行分支 | `parallel(name, branches, merger)` | 合并后的 `S` |
| 并行异步分支 | `parallelAsync(...)` | 合并后的 `S` |
| 路由 | `routingNode(name, fn)` | `NodeResult<S>` |

如果节点在多次执行间复用，其实现必须**线程安全**——无状态节点是常态。异步、并行与路由详见 **[[运行时特性|zh-Runtime-Features]]**。

## 边 (Edge)

边是**一等数据**——一个顶层 `record`，而非埋在构建器里的 lambda。它会被重放与可视化工具枚举。

- **无条件：** `edge("a", "b")`——总是可走。
- **有条件：** `edge("a", "b", predicate)`——仅当当前状态的谓词为真时可走。

**边谓词必须是状态的纯函数。** 它们在恢复与重放时会被重新求值，因此其中的副作用会破坏确定性。

## 状态 (State)

**状态对象就是一次运行的工作记忆**，在节点之间流动；每个节点返回下一个状态。record 是惯用选择——小巧、不可变、带 `withX(...)` 拷贝方法。

> `MemoryStore`（见 **[[记忆|zh-Memory]]**）用于*跨执行*数据；状态对象处理*执行内*数据。

## 上下文 (Context)

`Context` 随状态一起传入每个节点。它是**每次执行、每个节点**独有的，绝不跨执行共享。它暴露：

- `ctx.idempotencyKey()`——用于你自己节点级去重的稳定键（如 LLM/HTTP 调用去重）。
- `ctx.memory()`——已接入的 `MemoryStore`（默认 no-op）。见 **[[记忆|zh-Memory]]**。
- `ctx.reportUsage(promptTokens, completionTokens)`——向监听器上报 LLM token 用量，由 `ChatNode` 自动触发。

## 执行结果 (ExecutionResult)

`Graph.run(...)` 返回一个不可变的 `ExecutionResult<S>`：

| 组件 | 含义 |
|---|---|
| `executionId` | 本次运行的唯一 id（也是追踪/检查点的键） |
| `finalState` | 终止节点处（或停止处）的状态 |
| `path` | 已访问节点名的有序列表 |
| `status` | `COMPLETED`、`INTERRUPTED`、`TERMINATED`、`FAILED` … |
| `error` | 失败原因（如有） |

## SPI（扩展点）

TraceGraph 通过暴露**服务提供接口**让 `tracegraph-core` 保持精简；其他模块提供实现。

| SPI | 接入方式 | 用途 | 含实现的模块 |
|---|---|---|---|
| `NodeListener` | `.listener(...)` | span 形态的生命周期钩子 | `tracegraph-observability` |
| `TraceRecorder` | `.traceRecorder(...)` | 感知 executionId 的步骤记录，用于重放 | `tracegraph-observability` |
| `CheckpointStore` | `.checkpointStore(...)` | 用于恢复的持久化检查点 | `tracegraph-runtime` |
| `MemoryStore` | `.memoryStore(...)` | 作用域化的跨运行键值存储 | `tracegraph-memory` |
| `Guardrail<T>` | （节点逻辑） | ALLOW/BLOCK/TRANSFORM 内容门控 | `tracegraph-connectors` |

按设计，`NodeListener` **span 形态且对 executionId 无感知**；`TraceRecorder` **感知 executionId**。二者刻意分离。见 **[[可观测性与重放|zh-Observability-and-Replay]]**。

## 值得记住的约定

- `Node`/`Graph` 上**单一类型参数 `<S>`**。用状态组合，而非 `<S, R>`。
- **优先不可变状态。** 节点返回下一个状态。
- **边是一等数据**（顶层 record）。
- **除非 WHY 不显然，否则不写注释**——标识符名承载 WHAT。
- 编译器以 `-Xlint:all -Werror` 运行。

---

**下一步：** **[[执行模型|zh-Execution-Model]]**——执行器究竟如何运行一个图。
