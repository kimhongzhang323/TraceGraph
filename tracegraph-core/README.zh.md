# tracegraph-core（核心）

`tracegraph-core` 提供核心的图定义与同步执行语义：`Graph<S>` 构建器、`Node`、`Edge`、`ExecutionResult`、`Context`、重试策略与并行原语。该模块保持最小依赖，便于在其他模块中复用与扩展。

核心点：

- `Graph.builder()` 用于声明节点、入口与终止节点，并能附加监听器、trace 记录器与默认重试策略。
- 节点类型：同步节点、异步节点、并行分支、路由节点。
- 执行结果包含 `executionId`、最终状态、执行路径、状态与错误信息，便于回放与调试。

建议阅读顺序：`Graph` → `Node` 接口 → `Executor`（执行语义）→ `TraceRecorder` 钩子。
