# 可观测性与重放

差异化所在：**可以重放任意一次智能体执行，并附带完整的状态差异与推理追踪。** 以下内容都在 `tracegraph-observability`；`tracegraph-core` 保持无 OTel，仅暴露 SPI。

> 🌐 English: **[[Observability and Replay]]**

> 💡 想看一条记录下来的追踪到底长什么样——JSON、控制台输出、状态差异、成本报告？前往 **[[追踪输出示例|zh-Sample-Trace-Output]]**。

## 两个 SPI，两种形态

| SPI | 形态 | 感知 executionId? | 接入方式 |
|---|---|---|---|
| `NodeListener` | span 形态生命周期钩子 | **无感知**（按设计） | `.listener(...)` |
| `TraceRecorder` | 用于重放的步骤记录 | **感知** | `.traceRecorder(...)` |

二者刻意分离。监听器适合指标与追踪；记录器才能支持逐步重放。

## NodeListener

每个生命周期时刻一个回调。实现必须**线程安全**。

| 回调 | 触发时机 |
|---|---|
| `onEnter(node, state)` | 节点运行前 |
| `onExit(node, before, after)` | 节点成功后 |
| `onError(node, error)` | 失败时取代 `onExit` |
| `onRetry(...)` | 每次重试尝试 |
| `onState(name, before, after)` | 每次**成功**节点退出一次（失败/每次重试不触发） |
| `onUsage(nodeName, promptTokens, completionTokens)` | 任意 `ChatNode` 调用后（经 `ctx.reportUsage`） |

用 `Listeners.compose(...)` 组合多个监听器。

### OpenTelemetry

```java
.listener(OtelNodeListener.usingGlobal())
```

- **每节点一个 span。** 重试是同一 span 上的**事件**（不是每次尝试一个 span）。
- 错误设 `StatusCode.ERROR` 并调用 `Span.recordException`。
- **`parallel(...)` 内分支没有 span**（Phase 2c 契约——分支对监听器不可见）。
- 状态差异经 `NodeListener.onState` 流动，并绑定为 `state` span 事件，渲染 before/after 属性。渲染器经 `StateRenderer` 可插拔（默认 `String::valueOf`）。
- 发出 `llm.usage.input_tokens` / `output_tokens` / `total_tokens`，以及 OTel **GenAI 语义约定**属性（`gen_ai.system`、`gen_ai.request.model`、`gen_ai.usage.*`、`gen_ai.response.finish_reasons`）。

### 其他监听器（0.3.0）

| 监听器 | 用途 |
|---|---|
| `MicrometerNodeListener` | 桥接到 `MeterRegistry` 的 Prometheus 就绪计时器/计数器 |
| `SlowNodeListener` | 每节点 SLA 预算/告警 |
| `LlmCostListener` | 每执行与每节点 token 成本（同时是 `TraceRecorder`） |
| `CostBudgetListener` | 每模型定价 + `budgetUsd`；超额抛 `BudgetExceededException` |
| `TerminationListener<S>` | `maxTurns` / `afterNode` / `stateMatches` → 干净的 `Status.TERMINATED` |

## 追踪记录

接入 `TraceRecorder`，每一步都被捕获：

```java
TraceStore store = new InMemoryTraceStore();
Graph<S> graph = Graph.<S>builder()
        .traceRecorder(new RecordingTraceRecorder(store))
        .build();
```

- **每 executionId 一条追踪。** 恢复时**追加**到先前追踪（从 `TraceStore` 加载，种入进行中的 builder）。
- `parallel(...)` 内分支产生**一个步骤**（Phase 2c 契约）。
- 重试不产生额外步骤；`TraceStep.attempts` 记录次数。
- `ExecutionTrace<S>` / `TraceStep<S>` 是记录；`TraceStep.children` 携带子图步骤；`TraceStep.Usage(promptTokens, completionTokens)` 携带每步用量。

### 追踪存储

| 存储 | 后端 | 备注 |
|---|---|---|
| `InMemoryTraceStore` | map | 测试 |
| `JsonFileTraceStore<S>` | 每追踪一个 JSON 文件 | `JsonFileTraceStore.of(dir, stateType)`；原子写入；路径穿越防护；`Throwable` 有损往返（仅 className + message） |
| `JdbcTraceStore<S>` | 单表 | `JdbcTraceStore.of(dataSource, stateType[, table])`；反范式列 + `data_json` blob；`listIds()` 按 `started_at` 排序；失败抛 `TracePersistenceException` |
| `SamplingTraceStore` | 包装另一存储 | `random(rate)` / `slowExecutions(thresholdMs)` / `failedOnly()` |

`JsonFileTraceStore` / `JdbcTraceStore` 往返保留分叉与父级血缘。Jackson 是 observability 的可选依赖——仅当选用文件/JDBC 存储时引入。

## 重放

逐步遍历任意过往执行：

```java
ExecutionResult<OrderState> r = graph.run(seed);
ExecutionTrace<OrderState> trace =
        (ExecutionTrace<OrderState>) store.load(r.executionId()).orElseThrow();

Replayer<OrderState> replay = Replayer.of(trace);
for (int i = 0; i < replay.stepCount(); i++) {
    TraceStep<OrderState> step = replay.stepAt(i);
    System.out.printf("%d %s : %s -> %s%n",
            step.index(), step.nodeName(), step.before(), step.after());
}
```

### 从某步重执行（分叉）

```java
ReplayRunner<OrderState> runner = ReplayRunner.of(trace, graph);
ExecutionResult<OrderState> fork = runner.reRunFrom(1);   // 可选 seedOverride
// fork.executionId() != r.executionId()
// 新追踪记录 forkedFromExecutionId / forkedFromStepIndex
```

- `reRunFrom(stepIndex[, seedOverride])` 针对（可能修改过的）图从选定步骤重执行。
- `stepIndex == -1` 表示"从入口"；默认种子是 `parent.steps[stepIndex].before()`。
- 机制：`Graph.runFrom(startNode, seed, executionId)`——第三个执行器入口，**不**与 `CheckpointStore` 交互。
- **无确定性保证**——节点自负其确定性（LLM、HTTP、副作用）。

## 追踪差异比较

```java
TraceDiff<S> diff = TraceDiff.between(left, right);
diff.divergenceIndex();   // 首个不同的步骤
diff.sameStatus();
diff.sameFinalState();
diff.identical();         // 无分歧 + 同状态 + 同终态
diff.leftRemainder();
diff.rightRemainder();
```

`TraceDiff` 逐步遍历两条 `ExecutionTrace<S>`，给出最长公共前缀（按 `nodeName` + before/after 相等匹配）、分歧索引与各侧余量。它是纯数据 record，不耦合执行器或存储。

## 成本追踪

`LlmCostListener` 累计 token 用量，同时实现 `NodeListener` **与** `TraceRecorder`。同一实例经 `.listener(...)` 与 `.traceRecorder(...)` 接入，可捕获每执行**与**每节点细分：

```java
LlmCostListener cost = new LlmCostListener();
Graph<S> graph = Graph.<S>builder()
        .listener(cost)
        .traceRecorder(cost)
        .build();

graph.run(seed);
CostReport report = cost.snapshot(executionId);   // executionId, usageByNode, totalUsage
```

4 参的 `recordUsage(executionId, nodeName, ...)` 刻意跳过全局每节点桶——`onUsage` 拥有它——以避免双计。

## 多智能体 / 子图关联

- `ExecutionTrace` 携带 `parentExecutionId` / `parentStepIndex`（镜像 `forkedFromExecutionId` 血缘）。子图子追踪经执行器在调用内部图前调用的 `TraceRecorder.recordChildOf(...)` 钩子自动填充。
- `Graph.Builder.correlationId(Supplier<String>)` 把上游 APM id 传播到 `ExecutionTrace.correlationId` 与 OTLP span link。
- `Graph.Builder.sensitiveDataLogging(boolean)` 门控追踪中的提示词/响应捕获。

## 导出器

- `OtlpTraceExporter`——发出 span（子追踪上含 `tracegraph.parent.*` 属性）。
- `JsonlTraceExporter`——批量摄入 LangSmith / Langfuse / Arize。

---

**相关：** **[[LLM 连接器|zh-LLM-Connectors]]** · **[[评估|zh-Evaluation]]** · **[[REST API 参考|zh-REST-API-Reference]]**
