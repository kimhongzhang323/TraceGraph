# tracegraph-observability（可观测性与执行回放）

`tracegraph-observability` 模块为复杂的图执行流程提供了深度可见性。在构建多节点 Agent 工作流时，理解节点流转路径、状态变化、失败原因至关重要。本模块实现了 OpenTelemetry 监听器、详尽的执行轨迹（Trace）记录、存储机制以及强大的执行回放工具。

## 核心功能与架构：

1. **节点级事件监听 (NodeListener SPI)**：
   - 框架允许注册多个监听器。内置的 `OtelNodeListener` 会将每个节点的进入 (Enter)、退出 (Exit)、抛出异常与重试事件转化为标准的可观测性 Span 数据，自动上报至兼容 OpenTelemetry 的后端（如 Jaeger, Zipkin, Datadog），便于在 APM 系统中进行全链路追踪。

2. **详尽的轨迹记录与存储**：
   - **RecordingTraceRecorder**：在图执行期间记录每一步的详尽信息，包括输入状态、输出状态、耗时及产生的副作用。
   - **存储实现**：支持 `InMemoryTraceStore`（用于快速测试）和 `JsonFileTraceStore`（持久化到本地日志，方便日后审查）。

3. **回放与调试工具 (Replay & Diff)**：
   - **Replayer 与 ReplayRunner**：开发者可以随时加载一条历史 Trace，并指定从其中任意一个历史步骤重新开始执行。这在排查生产环境 bug 时极具价值——你可以直接在本地重现特定节点的输入状态。
   - **TraceDiff**：在重新执行或修改图结构后，可以使用差异比较工具自动对比新旧两次执行的状态变更路径，确保代码重构没有破坏原有的业务流转逻辑。
