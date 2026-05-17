---
title: REST API 参考
---

# REST API 参考

TraceGraph Spring Boot Starter 提供了一组 HTTP 端点，用于管理追踪记录、触发回放、恢复中断的运行以及流式传输执行事件。所有端点均以 `/tracegraph` 为路径前缀。

端点仅在满足相应条件时注册（有关开关属性，请参见[配置说明](configuration.md)）。

---

## 追踪记录

### 列出追踪记录

```
GET /tracegraph/traces
```

返回按 `started_at` 升序排列的执行 ID JSON 数组。

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `limit`  | int | （无）| 最多返回的 ID 数量。 |
| `offset` | int | 0     | 跳过的 ID 数量。 |

**响应头**

| 响应头 | 说明 |
|--------|------|
| `X-Total-Count` | 存储的追踪记录总数（不分页）。 |

**响应 — 200**

```json
["a1b2c3d4-...", "e5f6g7h8-..."]
```

**错误响应**

| 状态码 | 条件 |
|--------|------|
| 400 | `limit` 或 `offset` 为负数。 |

---

### 获取追踪记录

```
GET /tracegraph/traces/{id}
```

返回指定执行 ID 的完整 `ExecutionTrace` JSON。

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 执行 ID（UUID 字符串）。 |

**响应 — 200**

```json
{
  "executionId": "a1b2c3d4-...",
  "status": "COMPLETED",
  "startedAt": "2025-05-06T10:00:00Z",
  "completedAt": "2025-05-06T10:00:01Z",
  "steps": [
    {
      "nodeName": "fetch",
      "before": { "input": "hello" },
      "after":  { "input": "hello", "result": "world" },
      "attempts": 1,
      "usage": { "promptTokens": 0, "completionTokens": 0 }
    }
  ]
}
```

**错误响应**

| 状态码 | 条件 |
|--------|------|
| 404 | 未找到对应 `id` 的追踪记录。 |

---

### 删除追踪记录

```
DELETE /tracegraph/traces/{id}
```

删除指定执行 ID 的追踪记录。

**响应 — 204**（无响应体）

**错误响应**

| 状态码 | 条件 |
|--------|------|
| 404 | 未找到对应 `id` 的追踪记录。 |

---

### 比较两条追踪记录

```
GET /tracegraph/traces/{a}/diff/{b}
```

计算追踪记录 `a`（左）与追踪记录 `b`（右）之间的 `TraceDiff`。

**路径参数**

| 参数 | 说明 |
|------|------|
| `a` | 左侧执行 ID。 |
| `b` | 右侧执行 ID。 |

**响应 — 200**

```json
{
  "divergenceIndex": 2,
  "sameStatus": false,
  "sameFinalState": false,
  "identical": false,
  "leftRemainder":  [ ... ],
  "rightRemainder": [ ... ]
}
```

**错误响应**

| 状态码 | 条件 |
|--------|------|
| 404 | `a` 或 `b` 任意一个未知。 |

---

## 回放

### 回放追踪记录

```
POST /tracegraph/traces/{id}/replay?step=N
```

使用当前图定义，从步骤索引 `N` 开始重新执行已保存的追踪记录。返回新的执行 ID 和派生溯源信息。

仅当存在单个 `Graph<?>` Bean 时注册（`@ConditionalOnSingleCandidate`）。

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 待回放的源执行 ID。 |

**查询参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `step` | int | -1 | 回放的起始步骤索引。`-1` 表示从入口节点开始回放。 |

**响应 — 200**

```json
{
  "executionId": "new-uuid-...",
  "forkedFromExecutionId": "a1b2c3d4-...",
  "forkedFromStepIndex": 2,
  "status": "COMPLETED"
}
```

**错误响应**

| 状态码 | 条件 |
|--------|------|
| 404 | 未找到对应 `id` 的追踪记录。 |
| 400 | `step` 超出已保存追踪记录的范围。 |

---

## 中断

### 恢复中断的运行

```
POST /tracegraph/traces/{id}/resume
```

从检查点继续处于 `INTERRUPTED` 状态的运行。

注册在 `TraceReplayController` 上（条件与回放端点相同）。

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 中断运行的执行 ID。 |

**响应 — 200**

```json
{
  "executionId": "a1b2c3d4-...",
  "status": "COMPLETED"
}
```

**错误响应**

| 状态码 | 条件 |
|--------|------|
| 404 | 未找到对应 `id` 的追踪记录。 |
| 409 | 该运行存在，但状态不是 `INTERRUPTED`。 |

---

## 流式传输

### 流式传输执行事件（SSE）

```
POST /tracegraph/traces/stream
```

启动新的图执行并以 Server-Sent Events 形式流式传输 `NodeEvent<S>` 对象。事件类型包括：`NodeEnter`、`NodeExit`、`NodeRetry`、`Failed`、`Complete`。

仅当存在单个 `Graph<?>` Bean 时注册。

**请求体** — 种子状态对象（JSON 格式）。

**响应** — `text/event-stream`

```
event: NodeEnter
data: {"nodeName":"fetch","executionId":"..."}

event: NodeExit
data: {"nodeName":"fetch","state":{...},"executionId":"..."}

event: Complete
data: {"finalState":{...},"executionId":"...","status":"COMPLETED"}
```

背压溢出时丢弃最旧的事件；持久化记录保存在 `TraceStore` 中。

---

## Trace UI

以下端点在 `tracegraph-ui` 模块存在于类路径时注册。

### 图结构

```
GET /tracegraph/ui/graph
```

以 JSON 格式返回图的结构描述（节点、边、入口、终止节点、子图嵌套层次）。供 Trace UI 图形视图使用。

### 图复杂度

```
GET /tracegraph/ui/complexity
```

以 JSON 格式返回 `GraphComplexity` 记录。字段定义请参见[图复杂度](complexity.md)。
