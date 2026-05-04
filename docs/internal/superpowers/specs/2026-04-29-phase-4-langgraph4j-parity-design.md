# Phase 4 — Closing parity gaps with langgraph4j

**Date:** 2026-04-29
**Status:** Design — pending implementation plan
**Scope:** Phase 4 only. Phases 5–7 appended as roadmap, not specified.

## Why

A developer evaluating TraceGraph against langgraph4j today will find five concrete features missing that aren't gated by architectural disagreement — they're just unbuilt. Until they exist, TraceGraph's deeper replay/observability/retry story doesn't get a fair hearing because the evaluation stops at "no streaming" or "can't pause for human input."

Phase 4 is the smallest set of changes that removes those stop-gates without compromising the core's minimal-deps stance or the existing replay/trace contracts.

## Non-goals

- Studio UI, LangChain4j/Spring AI adapters, vector memory, deterministic LLM replay, cost tracking — all deferred to Phases 5–7.
- Migrating to a channels/reducers state model. TraceGraph's single-`<S>` composition stays.
- Project Reactor or RxJava. Streaming uses `java.util.concurrent.Flow` from the JDK.

## Phase 4 features

### 4a. Streaming execution

**Goal:** consumer can observe per-node progress as a graph runs, without waiting for `ExecutionResult`.

**API (in `tracegraph-core`):**

```java
public sealed interface NodeEvent<S> {
    record NodeEnter<S>(String executionId, String nodeName, S before) implements NodeEvent<S> {}
    record NodeExit<S>(String executionId, String nodeName, S before, S after) implements NodeEvent<S> {}
    record NodeRetry<S>(String executionId, String nodeName, int attempt, Throwable cause) implements NodeEvent<S> {}
    record Complete<S>(String executionId, ExecutionResult<S> result) implements NodeEvent<S> {}
    record Failed<S>(String executionId, String nodeName, Throwable cause) implements NodeEvent<S> {}
}

// On Graph<S>
Flow.Publisher<NodeEvent<S>> stream(S initial);
Flow.Publisher<NodeEvent<S>> stream(S initial, String executionId);
```

**Mechanics:**
- Implementation is a `NodeListener` adapter that pushes events onto a `SubmissionPublisher<NodeEvent<S>>`. Reuses existing listener plumbing — no new execution path.
- Backpressure: default `SubmissionPublisher` buffer; document that slow consumers may drop events on overflow (drop policy: oldest, since trace store has the durable record).
- Sync `run()` is unchanged. `stream()` returns immediately; the actual execution runs on the configured executor (or the lazy virtual-thread executor).

**Why `Flow` not Reactor:** core stays JDK-only. Adapters to Reactor/RxJava are trivial one-liners users write themselves; we don't ship them.

### 4b. Human-in-the-loop interrupts

**Goal:** declaratively pause execution before/after specific nodes; resume later from the same point.

**API (on `Graph.Builder<S>`):**

```java
Builder<S> interruptBefore(String... nodeNames);
Builder<S> interruptAfter(String... nodeNames);
```

**Mechanics:**
- Executor checks the interrupt sets at the natural seams: before invoking a node body, and immediately after `NodeListener.onState` fires.
- On interrupt: write a checkpoint with `lastCompletedNode` set appropriately, set `ExecutionResult.status` to a new value `INTERRUPTED`, return. No exception is thrown — interrupts are normal terminations.
- `Graph.resume(executionId)` already exists; it continues from the saved checkpoint. Edge resolution semantics are unchanged.
- Interrupt-before fires once and is cleared on resume (so resume doesn't immediately interrupt again on the same node). Tracked via a per-execution flag in the checkpoint payload (one new field on the checkpoint record — additive, backward-compatible Jackson).
- Interaction with retries: retries happen *inside* a node; interrupts happen *between* nodes. They don't interleave.
- Interaction with parallel: `interruptBefore` on a node containing `parallel(...)` interrupts before any branch runs. Per-branch interrupts are not supported (branches stay anonymous, per existing contract).

**New `Status` enum value:** `INTERRUPTED`. Existing `RUNNING`/`SUCCEEDED`/`FAILED` unchanged.

### 4c. Subgraphs

**Goal:** compose a compiled `Graph<S>` as a node inside a parent `Graph<S>`.

**API (in `tracegraph-core`):**

```java
// On Graph.Builder<S>
Builder<S> subgraph(String name, Graph<S> inner);
Builder<S> subgraph(String name, Graph<S> inner, RetryPolicy retry);
```

**Constraint:** parent and child share state type `<S>`. Cross-state subgraphs require a wrapper node (the user writes the projection); we don't bake projection into core.

**Trace shape:** subgraph execution produces a single parent `TraceStep` whose new `children` field holds the inner trace's steps. **This is a record evolution** on `TraceStep<S>` — adds an optional component `List<TraceStep<S>> children` (default empty list). Per `api-design.md`, this is a minor-bump break for positional consumers; document in `CHANGELOG.md`, deprecate any existing positional constructors with `@Deprecated(forRemoval = false)` initially, schedule removal at 1.0.

**Listener / OTel:** subgraph node emits one parent span; child nodes emit child spans under it (natural OTel parent-child via `OtelNodeListener` already supports this).

**Checkpoint:** the parent execution's checkpoint records the subgraph as a single completed node once it terminates. Mid-subgraph crash semantics: subgraph re-runs from its own start (at-least-once contract preserved). Nested resume (resuming a parent into the middle of a subgraph) is **out of scope for Phase 4** — document the limitation.

### 4d. Dynamic routing (`goTo`)

**Goal:** a node decides its own successor at runtime, without forcing all branching into edge predicates.

**API:**

```java
public sealed interface NodeResult<S> {
    record Continue<S>(S state) implements NodeResult<S> {}
    record GoTo<S>(String nodeName, S state) implements NodeResult<S> {}

    static <S> NodeResult<S> of(S state) { return new Continue<>(state); }
    static <S> NodeResult<S> goTo(String nodeName, S state) { return new GoTo<>(nodeName, state); }
}
```

`Node<S>` stays as-is (returns `S`). A new sibling functional interface `RoutingNode<S>` returns `NodeResult<S>`. Add `Builder.routingNode(name, RoutingNode<S>)` and `Builder.routingNode(name, RoutingNode<S>, RetryPolicy)`.

**Edge interaction:** when a routing node returns `GoTo`, outgoing edges are bypassed; the executor goes directly to the named node. `Continue` falls through to normal edge resolution. Validation: `GoTo` target must be a declared node name; unknown name fails the execution with `NodeExecutionException`.

**Replay:** `ReplayRunner.reRunFrom` works unchanged — the trace records the actual successor taken; replaying re-runs the routing node which may pick a different successor (no determinism guarantee, consistent with existing replay contract).

### 4e. Graph visualization

**Goal:** export the static graph as Mermaid or PlantUML for docs and Studio.

**API (on `Graph<S>`):**

```java
String toMermaid();
String toPlantUml();
```

**Mechanics:** pure structural render of nodes + edges. No execution required. Implementation is string templating in core (no new deps). Edge predicates render as labeled edges with the predicate's `toString()` if non-default, else unlabeled. Parallel branches render as a fan-out/fan-in. Subgraphs render as a nested cluster (Mermaid `subgraph`, PlantUML `package`).

## Module placement

All five features land in `tracegraph-core` except where they require existing optional modules:
- `Flow` streaming → core (JDK-only).
- Interrupts → core (executor change + checkpoint field).
- Subgraphs → core; `TraceStep.children` evolution affects `tracegraph-observability`.
- `goTo` → core.
- Mermaid/PlantUML → core.

No new modules. No new optional deps. Spring Boot starter gets:
- A `streamSse` endpoint on `TraceController` exposing `Graph.stream` as Server-Sent Events when a `Graph<?>` bean is present (additive; same conditions as existing replay endpoint).
- An interrupt/resume REST surface: `POST /tracegraph/traces/{id}/resume` (404 if unknown; 409 if not in `INTERRUPTED` state).

## Backward compatibility

| Change | Compat impact | Mitigation |
|---|---|---|
| `TraceStep.children` added | Breaks positional record consumers | Minor bump; CHANGELOG entry; convenience static factory `TraceStep.leaf(...)` for the common case |
| `Status.INTERRUPTED` enum value added | Breaks exhaustive switches | Document; switches in user code typically have `default` — low risk |
| `NodeResult` / `RoutingNode` added | Purely additive | None |
| `stream()`, `toMermaid()`, `toPlantUml()`, `interruptBefore/After`, `subgraph()` builder methods | Additive | None |
| New checkpoint field for interrupt-pending flag | Optional Jackson field | Old checkpoints deserialize cleanly (default `false`) |

## Testing strategy

Mirrors existing module test layout — one behavior cluster per test class.

- **`GraphStreamingTest`** (core): subscribes to `stream()`, asserts event order matches sync `run()`, asserts backpressure drop policy, asserts errors surface as `Failed` events.
- **`GraphInterruptTest`** (core): interrupt-before fires, checkpoint written with `INTERRUPTED` status, resume continues past the interrupted node exactly once.
- **`GraphSubgraphTest`** (core): nested execution, trace `children` populated, OTel parent/child spans (in observability module test).
- **`RoutingNodeTest`** (core): `goTo` bypasses edges, unknown target rejected, `Continue` falls through to edges.
- **`GraphVisualizationTest`** (core): Mermaid/PlantUML output is stable (golden-file comparison), parallel/subgraph rendering.
- **Spring Boot starter test**: SSE streaming endpoint + resume endpoint.

No mocks for the executor — tests run real graphs against the real executor.

## Open questions deferred to implementation

- **SSE vs WebSocket** for the starter streaming endpoint. Default to SSE (stateless, proxy-friendly); revisit if downstream needs bi-directional.
- **Mermaid version target.** Render against Mermaid v10 syntax; older versions may not parse newer cluster syntax. Document.
- **Subgraph trace size.** Deeply nested subgraphs could produce large traces. No truncation for Phase 4; revisit if real workloads hit it.

## Roadmap (informational, not specified here)

**Phase 5 — Differentiation depth:** deterministic replay (record LLM I/O), cost/token tracking, vector/semantic memory (`EmbeddingMemoryStore` + pgvector impl), `tracegraph-testing` module.

**Phase 6 — Ecosystem reach:** LangChain4j adapter (pulls in MCP + tool-calling), Spring AI adapter, `AgentNode<S>` tool-call loop primitive, cookbook samples (Adaptive RAG, supervisor, swarm, handoff).

**Phase 7 — Studio:** React + Spring Boot bundled UI; graph viz, live thread runner, state-diff viewer (uses `TraceDiff`), fork-from-step UI (uses `ReplayRunner`), OTel waterfall, cost dashboard, interrupt/resume controls, memory inspector. Auth + multi-tenancy.

The phase ordering is deliberate: Phase 4 unblocks evaluators; Phase 5 deepens the existing moat (replay/observability/memory); Phase 6 removes ecosystem switching cost; Phase 7 is the headline demo, and is only buildable cheaply *after* 4–5 land because Studio's differentiating views (diff, fork-from-step, deterministic replay) consume primitives those phases produce.
