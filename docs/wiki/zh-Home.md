# TraceGraph 维基

**面向 JVM 的生产级智能体运行时。** 类型化的图、持久化记忆、深度可观测性。

TraceGraph 是一个 JVM 原生的智能体运行时，用于构建带有持久化状态、重试、检查点、记忆与可观测性钩子的类型化执行图。它面向希望在 JVM 上进行图式编排、同时不放弃强类型、可测试性与生产可控性的团队。它**并非** LangGraph 的逐行移植——重点在于可靠性、可调试性，以及与 Java / Spring 生态的干净集成。

> 🌐 English: **[[Home]]**
>
> **当前版本：** `0.3.0`（2026-05-24） · **要求：** JDK 21 · **许可证：** Apache 2.0

---

## 从这里开始

| 你想做什么 | 前往 |
|---|---|
| 安装并运行第一个图 | **[[快速开始|zh-Getting-Started]]** |
| 跟随循序渐进的教程 | **[[教程|zh-Tutorial]]** |
| 直接复制可用的解决方案 | **[[实用手册|zh-Cookbook]]** |
| 理解图、节点、边、状态 | **[[核心概念|zh-Core-Concepts]]** |
| 了解一次运行如何执行 | **[[执行模型|zh-Execution-Model]]** |
| 理解设计取舍 | **[[架构设计|zh-Architecture]]** |
| 查看每个模块的职责 | **[[模块|zh-Modules]]** |

## 能力一览

| 领域 | 页面 |
|---|---|
| 重试、异步、并行、检查点、恢复、中断、子图、路由、流式 | **[[运行时特性|zh-Runtime-Features]]** |
| 作用域化的跨运行键值记忆（内存 / 文件 / JDBC） | **[[记忆|zh-Memory]]** |
| OpenTelemetry、追踪记录、重放、差异比较、成本追踪 | **[[可观测性与重放|zh-Observability-and-Replay]]** |
| LLM 客户端（OpenAI、Anthropic …）、ChatNode、ReAct | **[[LLM 连接器|zh-LLM-Connectors]]** |
| 交接、群聊、投票、角色/工具隔离 | **[[多智能体模式|zh-Multi-Agent-Patterns]]** |
| 检索增强生成、向量库 | **[[RAG 检索增强|zh-RAG]]** |
| 自动配置、REST 端点、依赖注入 | **[[Spring Boot 集成|zh-Spring-Boot-Integration]]** |
| 黄金追踪重放、BLEU/ROUGE/F1、CI 门禁 | **[[评估|zh-Evaluation]]** |
| 全部 `/tracegraph/*` HTTP 端点 | **[[REST API 参考|zh-REST-API-Reference]]** |
| 常见问题 | **[[常见问题|zh-FAQ]]** |

---

## 为什么选择 TraceGraph

- 用普通 Java 函数定义类型化的图
- 确定性的执行路径与显式的状态转移
- 内置重试、异步节点与并行扇出支持
- 面向长流程的检查点与恢复钩子
- 用于调试的追踪记录与重放支持
- 面向生产可观测性的 OpenTelemetry 集成
- 面向应用集成的 Spring Boot 自动配置

**适合**：希望对节点边界有显式控制的图式编排、易于测试的可预测状态转移、生产钩子（重试、检查点、追踪重放、OpenTelemetry），以及对普通 Java / Spring 习惯友好的 JVM 优先库。

**可能不适合**：想要无代码编排 UI、主要由提示词而非应用代码驱动的隐式控制流，或一个托管运行时/存储/追踪的开箱即用平台。

## 杀手级差异化

> **可以重放任意一次智能体执行，并附带完整的状态差异与推理追踪。**

接入一个 `TraceRecorder`，运行图，你就能逐节点查看 before/after 状态、比较两次运行、并从任意步骤针对修改后的图重新执行。见 **[[可观测性与重放|zh-Observability-and-Replay]]**。

---

## 30 秒上手

```java
record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
    OrderState withValid(boolean v)   { return new OrderState(id, v, charged, shipped); }
    OrderState withCharged(boolean v) { return new OrderState(id, valid, v, shipped); }
    OrderState withShipped(boolean v) { return new OrderState(id, valid, charged, v); }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge",   (state, ctx) -> state.withCharged(true))
        .node("ship",     (state, ctx) -> state.withShipped(true))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)
        .edge("charge", "ship")
        .terminal("ship")
        .build();

ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));
```

`Graph.run(...)` 背后没有隐藏的调度器或不透明的智能体循环。**图的定义就是控制流。**

---

## 项目状态

TraceGraph 处于活跃开发中（1.0 之前）。`0.x` 版本之间可能有破坏性变更；每一处变更都记录在 [`CHANGELOG.md`](https://github.com/kimhongzhang323/TraceGraph/blob/main/CHANGELOG.md)。

> ℹ️ 本维基镜像仓库内文档（`README.md`、`docs/site/docs` 与各模块 README）。如有疑问，发布产物才是真正的兼容性边界。
