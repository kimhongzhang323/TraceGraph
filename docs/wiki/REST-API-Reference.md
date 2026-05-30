# REST API Reference

The `tracegraph-spring-boot-starter` exposes HTTP endpoints for managing traces, triggering replays, resuming interrupted runs, and streaming execution events. All endpoints are under the **`/tracegraph`** path prefix.

Endpoints register only when their conditions are met — see **[[Spring Boot Integration]]** for the toggle properties (`tracegraph.web.enabled`, etc.). The replay / resume / stream controllers require a **single** `Graph<?>` bean (`@ConditionalOnSingleCandidate`).

---

## Traces

### List traces

```
GET /tracegraph/traces
```

Returns a JSON array of execution IDs, ordered by `started_at` ascending.

| Query param | Type | Default | Description |
|---|---|---|---|
| `limit` | int | (none) | max IDs to return |
| `offset` | int | 0 | IDs to skip |

| Response header | Description |
|---|---|
| `X-Total-Count` | unpaginated total number of stored traces |

**200**
```json
["a1b2c3d4-...", "e5f6g7h8-..."]
```

| Error | Condition |
|---|---|
| 400 | `limit` or `offset` is negative |

### Get a trace

```
GET /tracegraph/traces/{id}
```

Returns the full `ExecutionTrace` JSON.

**200**
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

| Error | Condition |
|---|---|
| 404 | no trace found for `id` |

### Delete a trace

```
DELETE /tracegraph/traces/{id}
```

**204** (no body) · **404** if unknown.

### Diff two traces

```
GET /tracegraph/traces/{a}/diff/{b}
```

Computes a `TraceDiff` between trace `a` (left) and `b` (right). See **[[Observability and Replay]]**.

**200**
```json
{
  "divergenceIndex": 2,
  "sameStatus": false,
  "sameFinalState": false,
  "identical": false,
  "leftRemainder":  [],
  "rightRemainder": []
}
```

| Error | Condition |
|---|---|
| 404 | either `a` or `b` is unknown |

---

## Replay

```
POST /tracegraph/traces/{id}/replay?step=N
```

Re-executes a saved trace from step index `N` using the current graph definition. Returns a new execution ID and fork lineage.

| Query param | Type | Default | Description |
|---|---|---|---|
| `step` | int | -1 | step to replay from; `-1` = replay from the entry node |

**200**
```json
{
  "executionId": "new-uuid-...",
  "forkedFromExecutionId": "a1b2c3d4-...",
  "forkedFromStepIndex": 2,
  "status": "COMPLETED"
}
```

| Error | Condition |
|---|---|
| 404 | no trace found for `id` |
| 400 | `step` out of range for the stored trace |

---

## Interrupts — resume

```
POST /tracegraph/traces/{id}/resume
```

Continues a run that is in `INTERRUPTED` status from its checkpoint. See interrupts in **[[Runtime Features]]**.

**200**
```json
{ "executionId": "a1b2c3d4-...", "status": "COMPLETED" }
```

| Error | Condition |
|---|---|
| 404 | no trace found for `id` |
| 409 | run exists but is not in `INTERRUPTED` status |

---

## Streaming (SSE)

```
POST /tracegraph/traces/stream
```

Starts a new execution and streams `NodeEvent<S>` objects as Server-Sent Events. Event types: `NodeEnter`, `NodeExit`, `NodeRetry`, `Failed`, `Complete`.

**Request body** — the seed state object as JSON.
**Response** — `text/event-stream`:

```
event: NodeEnter
data: {"nodeName":"fetch","executionId":"..."}

event: NodeExit
data: {"nodeName":"fetch","state":{...},"executionId":"..."}

event: Complete
data: {"finalState":{...},"executionId":"...","status":"COMPLETED"}
```

Durable records also live in the `TraceStore`.

---

## Trace UI (when `tracegraph-ui` is present)

```
GET /tracegraph/ui/graph        → graph structure (nodes, edges, entry, terminals, subgraph nesting) as JSON
GET /tracegraph/ui/complexity   → GraphComplexity record as JSON
```

---

## A2A (when `tracegraph-a2a` is present)

```
POST /a2a/messages              → deliver an agent-to-agent message
```

See **[[Multi-Agent Patterns]]**.

---

**Related:** **[[Spring Boot Integration]]** · **[[Observability and Replay]]**
