# tracegraph-observability（可观测性与回放）

该模块实现了 OpenTelemetry 监听器、Trace 记录/存储与回放工具：用于记录每个节点的进入/退出、状态变更，并提供回放（replay）与差异比较（diff）工具，便于调试与审计。

主要组件：

- `NodeListener` SPI 与 `OtelNodeListener`。
- `RecordingTraceRecorder`、`InMemoryTraceStore`、`JsonFileTraceStore`。
- `Replayer` 与 `ReplayRunner` 支持从历史步骤重新执行并记录 fork 衍生。
