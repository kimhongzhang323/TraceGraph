![TraceGraph 横幅](docs/images/banner.png)

# TraceGraph（中文）

> 本文档由 AI 自动翻译并经过自动校验；建议中文母语者审校后发布为最终版本。

TraceGraph 是一个面向 JVM 的执行图运行时，支持强类型状态、重试、检查点、持久化内存和可观测性钩子。

本仓库包含多个模块，用户可以按需引入最小子集：核心运行时、持久化/恢复、内存存储、可观测性与回放、以及 Spring Boot 启动器和 LLM 连接器。

主要特性摘要：

- 强类型图定义与节点边（纯 Java record + 函数）
- 可重试的节点、异步节点、并行分支与发送/合并
- 检查点与恢复（可插拔的 `CheckpointStore`）
- 作用域内的跨执行内存（`MemoryStore` SPI）
- Trace 记录、回放与差异比较（`TraceRecorder` / `TraceStore`）
- OpenTelemetry 节点监听器（可组合）
- Spring Boot 自动配置启动器（可选）
- 连接器模块提供 OpenAI / Anthropic 适配器（实验性）

快速开始（精简）示例：

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge", (state, ctx) -> state.withCharged(true))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)
        .terminal("charge")
        .build();

ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));
```

更多细节请参见仓库英文版 `README.md`，以及 `docs/site/docs/zh` 下的翻译页面（持续同步）。

构建与测试：

```bash
mvn -B -ntp verify
```

建议流程：先阅读 `tracegraph-core`（核心 API），再依次查看 runtime、memory、observability、connectors 和 spring-boot-starter 模块的翻译说明。

---

贡献与校对：

机器翻译可能包含术语与风格问题。建议在合并前由中文母语贡献者校对并调整术语（例如将 `Trace` 翻译为 `执行轨迹` 或 `追踪`，根据上下文一致处理）。

如果你希望我继续把更多页面自动翻译并创建对应文件，我可以继续逐模块生成翻译草稿并在每个文件顶部加入 `AI 翻译` 注记，便于后续人工校正。
