# Sample Trace Output

What a recorded trace actually looks like — console output, the persisted JSON, a state diff, a trace diff, streamed events, and a cost report. For the concepts behind these, see **[[Observability and Replay]]**.

> 🌐 中文版： **[[追踪输出示例|zh-Sample-Trace-Output]]**

All examples below come from one run of a small RAG graph:

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

## 1. Console output (iterating the trace)

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

Prints:

```text
execution 9f2a7c10-4b8e-4c3a-9e21-7d5b1f0a33c1 — COMPLETED (3 steps)
  0 retrieve  attempts=1  142 ms
  1 augment   attempts=1  0 ms
  2 generate  attempts=2  951 ms  tokens=312/48
```

`generate` shows `attempts=2` — the first LLM call failed (e.g. a 429) and the `RetryPolicy` recovered it. Note the retry produced **no extra step**; only the `attempts` counter moved. See **[[Execution Model]]**.

---

## 2. Persisted JSON

This is exactly what `JsonFileTraceStore` writes to disk (one file per execution, `{executionId}.json`), and the same structure `JdbcTraceStore` stores in its `data_json` column. Null/empty fields are omitted (Jackson `NON_NULL`); timestamps are ISO-8601; `durationNanos` is the per-step wall-clock time.

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
      "before": { "query": "What is our refund policy?", "retrievedChunks": ["...", "..."], "systemPrompt": null, "answer": null },
      "after":  { "query": "What is our refund policy?", "retrievedChunks": ["...", "..."], "systemPrompt": "Answer using only the context below.\nContext:\n...", "answer": null },
      "durationNanos": 90400
    },
    {
      "index": 2,
      "nodeName": "generate",
      "attempts": 2,
      "before": { "query": "What is our refund policy?", "retrievedChunks": ["...", "..."], "systemPrompt": "Answer using only...", "answer": null },
      "after":  { "query": "What is our refund policy?", "retrievedChunks": ["...", "..."], "systemPrompt": "Answer using only...", "answer": "You can get a full refund within 30 days of purchase; after that, store credit is offered." },
      "durationNanos": 951300000,
      "usage": { "promptTokens": 312, "completionTokens": 48 }
    }
  ]
}
```

### Field reference

`ExecutionTrace` (top level):

| Field | Notes |
|---|---|
| `executionId` | unique run id; also the store key |
| `status` | `COMPLETED` / `FAILED` / `INTERRUPTED` / `TERMINATED` |
| `initialState` / `finalState` | seed and final state |
| `error` | `{ "className", "message" }` — present only on failure (`Throwable` round-trip is lossy) |
| `steps` | ordered `TraceStep` array |
| `startedAt` / `completedAt` | ISO-8601 instants |
| `forkedFromExecutionId` / `forkedFromStepIndex` | replay lineage; `forkedFromStepIndex` defaults to `-1` |
| `parentExecutionId` / `parentStepIndex` | subgraph/multi-agent lineage (omitted when absent) |
| `correlationId` | upstream APM id, if `Graph.Builder.correlationId(...)` was set |

`TraceStep`:

| Field | Notes |
|---|---|
| `index` | 0-based position |
| `nodeName` | the node that produced this step |
| `attempts` | total tries (≥1); a retry increments this, not the step count |
| `before` / `after` | state on entry and exit |
| `durationNanos` | per-step wall-clock time |
| `usage` | `{ "promptTokens", "completionTokens" }` — present only when the node reported usage (e.g. a `ChatNode`) |
| `children` | nested steps for a `subgraph(...)` node (omitted when empty) |
| `error` | `{ "className", "message" }` on a failed step |
| `rawInput` / `rawOutput` | raw LLM prompt/response when `sensitiveDataLogging(true)` is enabled (omitted otherwise) |

> The REST endpoint `GET /tracegraph/traces/{id}` returns this same trace structure — see **[[REST API Reference]]**.

---

## 3. State diff for one step

The Trace UI (and the OTel `state` span event) renders each step's `before` → `after`. For step 2 (`generate`), only `answer` changed:

```diff
  query:           "What is our refund policy?"
  retrievedChunks: ["Refunds are accepted within 30 days...", "After 30 days..."]
  systemPrompt:    "Answer using only the context below.\nContext:\n..."
- answer:          null
+ answer:          "You can get a full refund within 30 days of purchase; after that, store credit is offered."
```

The renderer is pluggable via `StateRenderer` (default `String::valueOf`). See **[[Observability and Replay]]** and **[[Trace UI]]**.

---

## 4. Trace diff (comparing two runs)

After replaying from step 1 against a graph with a better prompt, `TraceDiff.between(original, forked)` yields:

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

Steps 0 and 1 matched (the longest common prefix); the runs first differ at step 2. This is what `GET /tracegraph/traces/{a}/diff/{b}` returns.

---

## 5. Streamed events (SSE)

`Graph.stream(initial)` (and `POST /tracegraph/traces/stream`) emits the same run as a live event sequence:

```text
event: NodeEnter
data: {"nodeName":"retrieve","executionId":"9f2a7c10-..."}

event: NodeExit
data: {"nodeName":"retrieve","state":{"retrievedChunks":["...","..."]},"executionId":"9f2a7c10-..."}

event: NodeEnter
data: {"nodeName":"augment","executionId":"9f2a7c10-..."}

event: NodeExit
data: {"nodeName":"augment","executionId":"9f2a7c10-..."}

event: NodeEnter
data: {"nodeName":"generate","executionId":"9f2a7c10-..."}

event: NodeRetry
data: {"nodeName":"generate","attempt":2,"executionId":"9f2a7c10-..."}

event: NodeExit
data: {"nodeName":"generate","state":{"answer":"You can get a full refund..."},"executionId":"9f2a7c10-..."}

event: Complete
data: {"finalState":{"answer":"You can get a full refund..."},"executionId":"9f2a7c10-...","status":"COMPLETED"}
```

The `NodeRetry` event corresponds to the `attempts=2` you saw in step 2 — streaming surfaces retries that the trace folds into a counter.

---

## 6. Cost report

If an `LlmCostListener` is wired via both `.listener(...)` and `.traceRecorder(...)`, `snapshot(executionId)` returns a `CostReport`:

```text
CostReport[
  executionId = 9f2a7c10-4b8e-4c3a-9e21-7d5b1f0a33c1,
  usageByNode = { generate = Usage[promptTokens=312, completionTokens=48] },
  totalUsage  = Usage[promptTokens=312, completionTokens=48]
]
```

Only `generate` appears — `retrieve` and `augment` made no LLM calls. See cost tracking in **[[Observability and Replay]]**.

---

**Related:** **[[Observability and Replay]]** · **[[REST API Reference]]** · **[[Trace UI]]** · **[[Tutorial]] → Part 10**
