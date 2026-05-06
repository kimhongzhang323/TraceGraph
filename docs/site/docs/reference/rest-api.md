# REST API Reference

The TraceGraph Spring Boot starter exposes a set of HTTP endpoints for managing traces, triggering replays, resuming interrupted runs, and streaming execution events. All endpoints are under the `/tracegraph` path prefix.

Endpoints are registered only when the relevant conditions are met (see [Configuration](configuration.md) for toggle properties).

---

## Traces

### List traces

```
GET /tracegraph/traces
```

Returns a JSON array of execution IDs, ordered by `started_at` ascending.

**Query parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `limit`   | int  | (none)  | Maximum number of IDs to return. |
| `offset`  | int  | 0       | Number of IDs to skip before returning. |

**Response headers**

| Header | Description |
|--------|-------------|
| `X-Total-Count` | Unpaginated total number of stored traces. |

**Response — 200**

```json
["a1b2c3d4-...", "e5f6g7h8-..."]
```

**Error responses**

| Status | Condition |
|--------|-----------|
| 400 | `limit` or `offset` is negative. |

---

### Get a trace

```
GET /tracegraph/traces/{id}
```

Returns the full `ExecutionTrace` JSON for the given execution ID.

**Path parameters**

| Parameter | Description |
|-----------|-------------|
| `id` | Execution ID (UUID string). |

**Response — 200**

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

**Error responses**

| Status | Condition |
|--------|-----------|
| 404 | No trace found for `id`. |

---

### Delete a trace

```
DELETE /tracegraph/traces/{id}
```

Removes the stored trace for the given execution ID.

**Response — 204** (no body)

**Error responses**

| Status | Condition |
|--------|-----------|
| 404 | No trace found for `id`. |

---

### Diff two traces

```
GET /tracegraph/traces/{a}/diff/{b}
```

Computes a `TraceDiff` between trace `a` (left) and trace `b` (right).

**Path parameters**

| Parameter | Description |
|-----------|-------------|
| `a` | Left execution ID. |
| `b` | Right execution ID. |

**Response — 200**

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

**Error responses**

| Status | Condition |
|--------|-----------|
| 404 | Either `a` or `b` is unknown. |

---

## Replay

### Replay a trace

```
POST /tracegraph/traces/{id}/replay?step=N
```

Re-executes a saved trace from step index `N` using the current graph definition. Returns a new execution ID and fork lineage.

Registered only when a single `Graph<?>` bean is present (`@ConditionalOnSingleCandidate`).

**Path parameters**

| Parameter | Description |
|-----------|-------------|
| `id` | Source execution ID to replay. |

**Query parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `step` | int | -1 | Step index to replay from. `-1` means replay from the entry node. |

**Response — 200**

```json
{
  "executionId": "new-uuid-...",
  "forkedFromExecutionId": "a1b2c3d4-...",
  "forkedFromStepIndex": 2,
  "status": "COMPLETED"
}
```

**Error responses**

| Status | Condition |
|--------|-----------|
| 404 | No trace found for `id`. |
| 400 | `step` is out of range for the stored trace. |

---

## Interrupts

### Resume an interrupted run

```
POST /tracegraph/traces/{id}/resume
```

Continues a run that is in `INTERRUPTED` status from its checkpoint.

Registered on `TraceReplayController` (same conditions as replay endpoint).

**Path parameters**

| Parameter | Description |
|-----------|-------------|
| `id` | Execution ID of an interrupted run. |

**Response — 200**

```json
{
  "executionId": "a1b2c3d4-...",
  "status": "COMPLETED"
}
```

**Error responses**

| Status | Condition |
|--------|-----------|
| 404 | No trace found for `id`. |
| 409 | Run exists but is not in `INTERRUPTED` status. |

---

## Streaming

### Stream execution events (SSE)

```
POST /tracegraph/traces/stream
```

Starts a new graph execution and streams `NodeEvent<S>` objects as Server-Sent Events. Event types: `NodeEnter`, `NodeExit`, `NodeRetry`, `Failed`, `Complete`.

Registered only when a single `Graph<?>` bean is present.

**Request body** — the seed state object as JSON.

**Response** — `text/event-stream`

```
event: NodeEnter
data: {"nodeName":"fetch","executionId":"..."}

event: NodeExit
data: {"nodeName":"fetch","state":{...},"executionId":"..."}

event: Complete
data: {"finalState":{...},"executionId":"...","status":"COMPLETED"}
```

Backpressure overflow drops the oldest event; durable records live in the `TraceStore`.

---

## Trace UI

The following endpoints are registered by the `tracegraph-ui` module when present on the classpath.

### Graph structure

```
GET /tracegraph/ui/graph
```

Returns the graph's structural description (nodes, edges, entry, terminals, subgraph nesting) as JSON. Used by the Trace UI graph view.

### Graph complexity

```
GET /tracegraph/ui/complexity
```

Returns a `GraphComplexity` record as JSON. See [Graph Complexity](complexity.md) for field definitions.
