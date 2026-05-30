# 追踪输出示例

一条记录下来的追踪到底长什么样——控制台输出、持久化 JSON、状态差异、追踪差异、流式事件与成本报告。背后的概念见 **[[可观测性与重放|zh-Observability-and-Replay]]**。

> 🌐 English: **[[Sample Trace Output]]**

以下示例均来自一个小型 RAG 图的一次运行：

```java
record RagState(String query, List<String> retrievedChunks, String systemPrompt, String answer) {}

InMemoryTraceStore store = new InMemoryTraceStore();
Graph<RagState> graph = Graph.<RagState>builder()
        .node("retrieve", retrieveNode, RetryPolicy.fixed(3, Duration.ofMillis(200)))
        .node("augment",  augmentNode)
        .node("generate", generateNode, RetryPolicy.fixed(2, Duration.ofSeconds(1)))
        .entry("retrieve")
        .edge("retrieve", "augment").edge("augment", "generate")
        .terminal("generate")
        .traceRecorder(new RecordingTraceRecorder(store))
        .build();

ExecutionResult<RagState> result = graph.run(
        new RagState("What is our refund policy?", List.of(), null, null));
```

---

## 1. 控制台输出（遍历追踪）

```java
ExecutionTrace<RagState> trace = (ExecutionTrace<RagState>) store.load(result.executionId()).orElseThrow();

System.out.printf("execution %s — %s (%d steps)%n",
        trace.executionId(), trace.status(), trace.steps().size());
for (TraceStep<RagState> s : trace.steps()) {
    System.out.printf("  %d %-9s attempts=%d  %d ms%s%n",
            s.index(), s.nodeName(), s.attempts(),
            s.duration().toMillis(),
            s.usage() == null ? "" : "  tokens=" + s.usage().promptTokens() + "/" + s.usage().completionTokens());
}
```

打印：

```text
execution 9f2a7c10-4b8e-4c3a-9e21-7d5b1f0a33c1 — COMPLETED (3 steps)
  0 retrieve  attempts=1  142 ms
  1 augment   attempts=1  0 ms
  2 generate  attempts=2  951 ms  tokens=312/48
```

`generate` 显示 `attempts=2`——第一次 LLM 调用失败（如 429），`RetryPolicy` 恢复了它。注意重试**不产生额外步骤**；只是 `attempts` 计数增加。见 **[[执行模型|zh-Execution-Model]]**。

---

## 2. 持久化 JSON

这正是 `JsonFileTraceStore` 写到磁盘的内容（每次执行一个文件 `{executionId}.json`），也是 `JdbcTraceStore` 存进 `data_json` 列的同一结构。null/空字段被省略（Jackson `NON_NULL`）；时间戳为 ISO-8601；`durationNanos` 是每步的墙钟时间。

```json
{
  "executionId": "9f2a7c10-4b8e-4c3a-9e21-7d5b1f0a33c1",
  "status": "COMPLETED",
  "initialState": {
    "query": "What is our refund policy?",
    "retrievedChunks": [],
    "systemPrompt": null,
    "answer": null
  },
  "finalState": {
    "query": "What is our refund policy?",
    "retrievedChunks": [
      "Refunds are accepted within 30 days of purchase.",
      "After 30 days, store credit is offered instead."
    ],
    "systemPrompt": "Answer using only the context below.\nContext:\n...",
    "answer": "You can get a full refund within 30 days of purchase; after that, store credit is offered."
  },
  "startedAt": "2026-05-30T09:14:02.001Z",
  "completedAt": "2026-05-30T09:14:03.187Z",
  "forkedFromStepIndex": -1,
  "steps": [
    {
      "index": 0,
      "nodeName": "retrieve",
      "attempts": 1,
      "before": { "query": "What is our refund policy?", "retrievedChunks": [], "systemPrompt": null, "answer": null },
      "after":  { "query": "What is our refund policy?", "retrievedChunks": [
        "Refunds are accepted within 30 days of purchase.",
        "After 30 days, store credit is offered instead."
      ], "systemPrompt": null, "answer": null },
      "durationNanos": 142800000
    },
    {
      "index": 1,
      "nodeName": "augment",
      "attempts": 1,
      "before": { "query": "...", "retrievedChunks": ["...", "..."], "systemPrompt": null, "answer": null },
      "after":  { "query": "...", "retrievedChunks": ["...", "..."], "systemPrompt": "Answer using only the context below.\nContext:\n...", "answer": null },
      "durationNanos": 90400
    },
    {
      "index": 2,
      "nodeName": "generate",
      "attempts": 2,
      "before": { "query": "...", "retrievedChunks": ["...", "..."], "systemPrompt": "Answer using only...", "answer": null },
      "after":  { "query": "...", "retrievedChunks": ["...", "..."], "systemPrompt": "Answer using only...", "answer": "You can get a full refund within 30 days of purchase; after that, store credit is offered." },
      "durationNanos": 951300000,
      "usage": { "promptTokens": 312, "completionTokens": 48 }
    }
  ]
}
```

### 字段参考

`ExecutionTrace`（顶层）：

| 字段 | 说明 |
|---|---|
| `executionId` | 唯一运行 id；也是存储键 |
| `status` | `COMPLETED` / `FAILED` / `INTERRUPTED` / `TERMINATED` |
| `initialState` / `finalState` | 种子与最终状态 |
| `error` | `{ "className", "message" }`——仅失败时出现（`Throwable` 往返有损） |
| `steps` | 有序的 `TraceStep` 数组 |
| `startedAt` / `completedAt` | ISO-8601 时刻 |
| `forkedFromExecutionId` / `forkedFromStepIndex` | 重放血缘；`forkedFromStepIndex` 默认 `-1` |
| `parentExecutionId` / `parentStepIndex` | 子图/多智能体血缘（无则省略） |
| `correlationId` | 上游 APM id（若设置了 `Graph.Builder.correlationId(...)`） |

`TraceStep`：

| 字段 | 说明 |
|---|---|
| `index` | 0 起的位置 |
| `nodeName` | 产生此步的节点 |
| `attempts` | 总尝试次数（≥1）；重试增加它，而非步骤数 |
| `before` / `after` | 进入与退出时的状态 |
| `durationNanos` | 每步墙钟时间 |
| `usage` | `{ "promptTokens", "completionTokens" }`——仅当节点上报用量时出现（如 `ChatNode`） |
| `children` | `subgraph(...)` 节点的嵌套步骤（空则省略） |
| `error` | 失败步骤上的 `{ "className", "message" }` |
| `rawInput` / `rawOutput` | 启用 `sensitiveDataLogging(true)` 时的原始 LLM 提示词/响应（否则省略） |

> REST 端点 `GET /tracegraph/traces/{id}` 返回同一追踪结构——见 **[[REST API 参考|zh-REST-API-Reference]]**。

---

## 3. 单步的状态差异

追踪界面（与 OTel `state` span 事件）渲染每步的 `before` → `after`。对步骤 2（`generate`），仅 `answer` 改变：

```diff
  query:           "What is our refund policy?"
  retrievedChunks: ["Refunds are accepted within 30 days...", "After 30 days..."]
  systemPrompt:    "Answer using only the context below.\nContext:\n..."
- answer:          null
+ answer:          "You can get a full refund within 30 days of purchase; after that, store credit is offered."
```

渲染器经 `StateRenderer` 可插拔（默认 `String::valueOf`）。见 **[[可观测性与重放|zh-Observability-and-Replay]]** 与 **[[追踪界面|zh-Trace-UI]]**。

---

## 4. 追踪差异（比较两次运行）

从步骤 1 针对改进提示词的图重放后，`TraceDiff.between(original, forked)` 给出：

```json
{
  "divergenceIndex": 2,
  "sameStatus": true,
  "sameFinalState": false,
  "identical": false,
  "leftRemainder":  [ { "index": 2, "nodeName": "generate", "after": { "answer": "You can get a full refund within 30 days..." } } ],
  "rightRemainder": [ { "index": 2, "nodeName": "generate", "after": { "answer": "Refunds: full within 30 days, store credit after." } } ]
}
```

步骤 0 与 1 匹配（最长公共前缀）；两次运行首次在步骤 2 不同。这正是 `GET /tracegraph/traces/{a}/diff/{b}` 的返回。

---

## 5. 流式事件（SSE）

`Graph.stream(initial)`（与 `POST /tracegraph/traces/stream`）把同一次运行作为实时事件序列发出：

```text
event: NodeEnter
data: {"nodeName":"retrieve","executionId":"9f2a7c10-..."}

event: NodeExit
data: {"nodeName":"retrieve","state":{"retrievedChunks":["...","..."]},"executionId":"9f2a7c10-..."}

event: NodeEnter
data: {"nodeName":"generate","executionId":"9f2a7c10-..."}

event: NodeRetry
data: {"nodeName":"generate","attempt":2,"executionId":"9f2a7c10-..."}

event: NodeExit
data: {"nodeName":"generate","state":{"answer":"You can get a full refund..."},"executionId":"9f2a7c10-..."}

event: Complete
data: {"finalState":{"answer":"You can get a full refund..."},"executionId":"9f2a7c10-...","status":"COMPLETED"}
```

`NodeRetry` 事件对应你在步骤 2 看到的 `attempts=2`——流式会暴露追踪折叠为计数的那次重试。

---

## 6. 成本报告

若 `LlmCostListener` 经 `.listener(...)` 与 `.traceRecorder(...)` 同时接入，`snapshot(executionId)` 返回一个 `CostReport`：

```text
CostReport[
  executionId = 9f2a7c10-4b8e-4c3a-9e21-7d5b1f0a33c1,
  usageByNode = { generate = Usage[promptTokens=312, completionTokens=48] },
  totalUsage  = Usage[promptTokens=312, completionTokens=48]
]
```

仅 `generate` 出现——`retrieve` 与 `augment` 未做 LLM 调用。见 **[[可观测性与重放|zh-Observability-and-Replay]]** 中的成本追踪。

---

**相关：** **[[可观测性与重放|zh-Observability-and-Replay]]** · **[[REST API 参考|zh-REST-API-Reference]]** · **[[追踪界面|zh-Trace-UI]]** · **[[教程|zh-Tutorial]] → 第 10 部分**
