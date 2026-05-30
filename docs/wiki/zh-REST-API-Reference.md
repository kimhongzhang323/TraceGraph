# REST API 参考

`tracegraph-spring-boot-starter` 暴露用于管理追踪、触发重放、恢复中断运行与流式执行事件的 HTTP 端点。所有端点位于 **`/tracegraph`** 路径前缀下。

端点仅在条件满足时注册——切换属性见 **[[zh-Spring-Boot-Integration|Spring Boot 集成]]**。replay / resume / stream 控制器要求**单个** `Graph<?>` bean。

> 🌐 本页为 AI 机翻草稿，需人工校对。English: **[[REST API Reference]]**

---

## 追踪

### 列出追踪

```
GET /tracegraph/traces
```

返回 executionId 的 JSON 数组，按 `started_at` 升序。

| 查询参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `limit` | int | （无） | 最多返回数 |
| `offset` | int | 0 | 跳过数 |

| 响应头 | 说明 |
|---|---|
| `X-Total-Count` | 未分页的存储追踪总数 |

**200**
```json
["a1b2c3d4-...", "e5f6g7h8-..."]
```

| 错误 | 条件 |
|---|---|
| 400 | `limit` 或 `offset` 为负 |

### 获取追踪

```
GET /tracegraph/traces/{id}
```

返回完整的 `ExecutionTrace` JSON。

**200**
```json
{
  "executionId": "a1b2c3d4-...",
  "status": "COMPLETED",
  "startedAt": "2025-05-06T10:00:00Z",
  "completedAt": "2025-05-06T10:00:01Z",
  "steps": [
    { "nodeName": "fetch", "before": { "input": "hello" },
      "after": { "input": "hello", "result": "world" },
      "attempts": 1, "usage": { "promptTokens": 0, "completionTokens": 0 } }
  ]
}
```

| 错误 | 条件 |
|---|---|
| 404 | 未找到 `id` 的追踪 |

### 删除追踪

```
DELETE /tracegraph/traces/{id}
```

**204**（无正文） · 未知则 **404**。

### 比较两条追踪

```
GET /tracegraph/traces/{a}/diff/{b}
```

计算追踪 `a`（左）与 `b`（右）之间的 `TraceDiff`。见 **[[zh-Observability-and-Replay|可观测性与重放]]**。

**200**
```json
{ "divergenceIndex": 2, "sameStatus": false, "sameFinalState": false,
  "identical": false, "leftRemainder": [], "rightRemainder": [] }
```

| 错误 | 条件 |
|---|---|
| 404 | `a` 或 `b` 未知 |

---

## 重放

```
POST /tracegraph/traces/{id}/replay?step=N
```

用当前图定义从步骤索引 `N` 重执行已保存追踪。返回新 executionId 与分叉血缘。

| 查询参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `step` | int | -1 | 重放起始步；`-1` = 从入口 |

**200**
```json
{ "executionId": "new-uuid-...", "forkedFromExecutionId": "a1b2c3d4-...",
  "forkedFromStepIndex": 2, "status": "COMPLETED" }
```

| 错误 | 条件 |
|---|---|
| 404 | 未找到 `id` 的追踪 |
| 400 | `step` 超出已存追踪范围 |

---

## 中断 —— 恢复

```
POST /tracegraph/traces/{id}/resume
```

从检查点继续处于 `INTERRUPTED` 状态的运行。见 **[[zh-Runtime-Features|运行时特性]]** 中的中断。

**200**
```json
{ "executionId": "a1b2c3d4-...", "status": "COMPLETED" }
```

| 错误 | 条件 |
|---|---|
| 404 | 未找到 `id` 的追踪 |
| 409 | 运行存在但不处于 `INTERRUPTED` |

---

## 流式（SSE）

```
POST /tracegraph/traces/stream
```

开始新执行并以 Server-Sent Events 流式发出 `NodeEvent<S>`。事件类型：`NodeEnter`、`NodeExit`、`NodeRetry`、`Failed`、`Complete`。

**请求正文**——种子状态对象 JSON。**响应**——`text/event-stream`：

```
event: NodeEnter
data: {"nodeName":"fetch","executionId":"..."}

event: Complete
data: {"finalState":{...},"executionId":"...","status":"COMPLETED"}
```

持久记录也在 `TraceStore`。

---

## 追踪界面（存在 `tracegraph-ui` 时）

```
GET /tracegraph/ui/graph        → 图结构（节点、边、入口、终止、子图嵌套）JSON
GET /tracegraph/ui/complexity   → GraphComplexity 记录 JSON
```

---

## A2A（存在 `tracegraph-a2a` 时）

```
POST /a2a/messages              → 投递智能体到智能体消息
```

见 **[[zh-Multi-Agent-Patterns|多智能体模式]]**。

---

**相关：** **[[zh-Spring-Boot-Integration|Spring Boot 集成]]** · **[[zh-Observability-and-Replay|可观测性与重放]]**
