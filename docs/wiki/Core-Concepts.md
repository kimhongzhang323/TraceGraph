# Core Concepts

The vocabulary you need to read and write any TraceGraph program. Everything here lives in `tracegraph-core`, which has zero heavy dependencies (SLF4J API only at runtime).

## Graph

`Graph<S>` is the main runtime abstraction. It is parameterised by a **single type parameter `<S>`** — the state type. There is no `<S, R>`; use state composition for sub-results.

A graph defines:

- named **nodes**
- directed **edges**
- one **entry** node
- one or more **terminal** nodes
- optional retry, checkpoint, trace, listener, memory, and executor behaviour

A `Graph<S>` is **immutable after `build()`** and safe to share across threads. Multiple `run` calls in parallel never corrupt graph state. The `Graph.Builder<S>` is **not** thread-safe — construct on a single thread.

```java
Graph<S> graph = Graph.<S>builder()
        .node("a", nodeA)
        .node("b", nodeB)
        .entry("a")
        .edge("a", "b")
        .terminal("b")
        .build();   // validates eagerly; throws GraphValidationException on bad input
```

The fluent builder is the contract — see the [API design rules](https://github.com/kimhongzhang323/TraceGraph/blob/main/.claude/rules/api-design.md).

## Node

A `Node<S>` is a functional interface: it receives the current typed state plus a `Context`, and returns the next state. **Nodes return the next state; they don't mutate** — immutable state is strongly preferred.

TraceGraph supports several execution styles:

| Style | Builder method | Returns |
|---|---|---|
| Synchronous | `node(name, fn)` | `S` |
| Asynchronous | `asyncNode(name, fn)` | `CompletableFuture<S>` |
| Parallel branches | `parallel(name, branches, merger)` | merged `S` |
| Parallel async branches | `parallelAsync(...)` | merged `S` |
| Routing | `routingNode(name, fn)` | `NodeResult<S>` |

Node implementations must be **thread-safe if reused across executions** — stateless nodes are the norm. See **[[Runtime Features]]** for async, parallel, and routing detail.

## Edge

Edges are **first-class data** — a top-level `record`, not a lambda buried in the builder. They will be enumerated by replay and visualization tooling.

- **Unconditional:** `edge("a", "b")` — always traversable.
- **Conditional:** `edge("a", "b", predicate)` — traversed only when the predicate of the current state is true.

**Edge predicates must be pure functions of state.** They are re-evaluated on resume and replay, so side effects there break determinism.

## State

The **state object is the working memory** of a run. It flows from node to node; each node returns the next state. Records are the idiomatic choice — small, immutable, with `withX(...)` copy methods for transitions.

> The `MemoryStore` (see **[[Memory]]**) is for *cross-execution* data — the state object handles *within-execution* data.

## Context

`Context` is passed to every node alongside state. It is **per-execution, per-node** and never shared across executions. It exposes:

- `ctx.idempotencyKey()` — a stable key for your own node-level deduplication (e.g. LLM/HTTP call dedup).
- `ctx.memory()` — the wired `MemoryStore` (default no-op). See **[[Memory]]**.
- `ctx.reportUsage(promptTokens, completionTokens)` — report LLM token usage to listeners. Fired automatically by `ChatNode`.

## ExecutionResult

`Graph.run(...)` returns an immutable `ExecutionResult<S>`:

| Component | Meaning |
|---|---|
| `executionId` | unique id for this run (also the trace / checkpoint key) |
| `finalState` | the state at the terminal node (or where the run stopped) |
| `path` | ordered list of node names visited |
| `status` | `COMPLETED`, `INTERRUPTED`, `TERMINATED`, `FAILED`, … |
| `error` | the failure, if any |

This makes the runtime straightforward to test and inspect.

## SPIs (extension points)

TraceGraph keeps `tracegraph-core` minimal by exposing **service provider interfaces** that other modules implement. The core SPIs:

| SPI | Wired via | Purpose | Module with impls |
|---|---|---|---|
| `NodeListener` | `.listener(...)` | span-shaped lifecycle hooks (enter/exit/retry/usage/state) | `tracegraph-observability` |
| `TraceRecorder` | `.traceRecorder(...)` | executionId-aware step recording for replay | `tracegraph-observability` |
| `CheckpointStore` | `.checkpointStore(...)` | durable checkpoints for resume | `tracegraph-runtime` |
| `MemoryStore` | `.memoryStore(...)` | scoped cross-run key-value store | `tracegraph-memory` |
| `Guardrail<T>` | (node logic) | ALLOW/BLOCK/TRANSFORM content gating | `tracegraph-connectors` |

`NodeListener` is **span-shaped and executionId-blind by design**; `TraceRecorder` is **executionId-aware**. They are deliberately separate. See **[[Observability and Replay]]**.

## Conventions worth knowing

- **Single type parameter `<S>`** on `Node`/`Graph`. Use state composition, not `<S, R>`.
- **Immutable state preferred.** Nodes return the next state.
- **Edges are first-class data** (record, top-level).
- **No comments unless the WHY is non-obvious** — identifier names carry the WHAT.
- Compiler runs with `-Xlint:all -Werror`.

---

**Next:** **[[Execution Model]]** — exactly how the executor runs a graph.
