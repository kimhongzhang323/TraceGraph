# Observability and Replay

The differentiator: **replay any agent execution with a full state diff and reasoning trace.** Everything here lives in `tracegraph-observability`; `tracegraph-core` stays OTel-free and exposes only the SPIs.

## Two SPIs, two shapes

| SPI | Shape | executionId? | Wired via |
|---|---|---|---|
| `NodeListener` | span-shaped lifecycle hooks | **blind** (by design) | `.listener(...)` |
| `TraceRecorder` | step recorder for replay | **aware** | `.traceRecorder(...)` |

They are deliberately separate. A listener is fine for metrics and tracing; a recorder is what enables step-by-step replay.

## NodeListener

One callback per lifecycle moment. Implementations must be **thread-safe** (Phase 2 calls may come from multiple worker threads).

| Callback | Fires |
|---|---|
| `onEnter(node, state)` | before a node runs |
| `onExit(node, before, after)` | after a successful node |
| `onError(node, error)` | instead of `onExit` on failure |
| `onRetry(...)` | on each retry attempt |
| `onState(name, before, after)` | once per **successful** node exit (not on failure, not per retry) |
| `onUsage(nodeName, promptTokens, completionTokens)` | after any `ChatNode` call (via `ctx.reportUsage`) |

Compose multiple listeners with `Listeners.compose(...)`.

### OpenTelemetry

```java
Graph<S> graph = Graph.<S>builder()
        .listener(OtelNodeListener.usingGlobal())
        .build();
```

- **One span per node.** Retries are span **events** on the same span (no span-per-attempt).
- Errors set `StatusCode.ERROR` and call `Span.recordException`.
- **Branches inside `parallel(...)` don't get spans** (Phase 2c contract — branches are invisible to listeners).
- State diffs flow through `NodeListener.onState` and bind as a `state` span event with rendered before/after attributes. The renderer is pluggable via `StateRenderer` (default `String::valueOf`).
- Emits `llm.usage.input_tokens` / `output_tokens` / `total_tokens` plus OTel **GenAI semantic-convention** attributes (`gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.*`, `gen_ai.response.finish_reasons`).

### Other listeners (0.3.0)

| Listener | Purpose |
|---|---|
| `MicrometerNodeListener` | Prometheus-ready timers/counters bridged to a `MeterRegistry` |
| `SlowNodeListener` | per-node SLA budgets / alerts |
| `LlmCostListener` | per-execution and per-node token cost (also a `TraceRecorder`) |
| `CostBudgetListener` | per-model pricing + `budgetUsd`; throws `BudgetExceededException` on overrun |
| `TerminationListener<S>` | `maxTurns` / `afterNode` / `stateMatches` → clean `Status.TERMINATED` |

## Trace recording

Plug in a `TraceRecorder` and every step is captured:

```java
TraceStore store = new InMemoryTraceStore();
Graph<S> graph = Graph.<S>builder()
        .traceRecorder(new RecordingTraceRecorder(store))
        .build();
```

- **One trace per executionId.** Resume **appends** to the prior trace (loaded from the `TraceStore`, seeding the in-flight builder).
- Branches inside `parallel(...)` produce **one step** (Phase 2c contract).
- Retries don't create extra steps; `TraceStep.attempts` records the count.
- `ExecutionTrace<S>` / `TraceStep<S>` are the records; `TraceStep.children` carries subgraph steps; `TraceStep.Usage(promptTokens, completionTokens)` carries per-step usage.

### Trace stores

| Store | Backing | Notes |
|---|---|---|
| `InMemoryTraceStore` | map | tests |
| `JsonFileTraceStore<S>` | one JSON file per trace | `JsonFileTraceStore.of(dir, stateType)`; atomic `*.tmp` + `ATOMIC_MOVE`; path-traversal guard; lossy `Throwable` round-trip (className + message only) |
| `JdbcTraceStore<S>` | single table | `JdbcTraceStore.of(dataSource, stateType[, table])`; denormalized columns + `data_json` blob; `listIds()` orders by `started_at`; `TracePersistenceException` on failure |
| `SamplingTraceStore` | wraps another store | `random(rate)` / `slowExecutions(thresholdMs)` / `failedOnly()` |

`JsonFileTraceStore` / `JdbcTraceStore` round-trip fork and parent lineage. Jackson is an optional dependency of observability — pulled in only when you opt into the file/JDBC stores.

## Replay

Step through any past execution:

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

### Re-execute from a step (fork)

```java
ReplayRunner<OrderState> runner = ReplayRunner.of(trace, graph);
ExecutionResult<OrderState> fork = runner.reRunFrom(1);          // optional seedOverride
// fork.executionId() != r.executionId()
// the new trace records forkedFromExecutionId / forkedFromStepIndex
```

- `reRunFrom(stepIndex[, seedOverride])` re-executes from a chosen step against a (possibly modified) graph.
- `stepIndex == -1` means "from entry"; default seed is `parent.steps[stepIndex].before()`.
- Mechanic: `Graph.runFrom(startNode, seed, executionId)` — the third executor entry point, **no** `CheckpointStore` interaction.
- **No determinism guarantee** — nodes own their own determinism (LLM, HTTP, side effects).

## Trace diffing

```java
TraceDiff<S> diff = TraceDiff.between(left, right);
diff.divergenceIndex();   // first step where they differ
diff.sameStatus();        // run-level outcome equal?
diff.sameFinalState();
diff.identical();         // no divergence + same status + same final state
diff.leftRemainder();     // steps after divergence on each side
diff.rightRemainder();
```

`TraceDiff` walks two `ExecutionTrace<S>` step-by-step, surfacing a longest common prefix (matched by `nodeName` + before/after equality), the divergence index, and per-side remainders. It is a pure-data record with no executor or store coupling.

## Cost tracking

`LlmCostListener` accumulates token usage and implements **both** `NodeListener` and `TraceRecorder`. Wire the same instance via both `.listener(...)` and `.traceRecorder(...)` to capture per-execution **and** per-node breakdown:

```java
LlmCostListener cost = new LlmCostListener();
Graph<S> graph = Graph.<S>builder()
        .listener(cost)
        .traceRecorder(cost)
        .build();

graph.run(seed);
CostReport report = cost.snapshot(executionId);   // executionId, usageByNode, totalUsage
```

The 4-arg `recordUsage(executionId, nodeName, ...)` deliberately skips the global per-node bucket — `onUsage` owns it — so dual wiring doesn't double-count.

## Multi-agent / subgraph correlation

- `ExecutionTrace` carries `parentExecutionId` / `parentStepIndex` (mirrors `forkedFromExecutionId` lineage). Subgraph child traces auto-populate this via the `TraceRecorder.recordChildOf(...)` hook the executor calls before invoking the inner graph.
- `Graph.Builder.correlationId(Supplier<String>)` propagates an upstream APM id to `ExecutionTrace.correlationId` and OTLP span links.
- `Graph.Builder.sensitiveDataLogging(boolean)` gates prompt/response capture in traces.

## Exporters

- `OtlpTraceExporter` — emits spans (including `tracegraph.parent.*` attributes on child traces).
- `JsonlTraceExporter` — batch ingestion into LangSmith / Langfuse / Arize.

---

**Related:** **[[LLM Connectors]]** (token usage source) · **[[Evaluation]]** (golden-trace assertions) · **[[REST API Reference]]** (trace endpoints)
