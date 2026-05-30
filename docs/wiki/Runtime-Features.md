# Runtime Features

Everything the executor does beyond a plain node-to-node walk. Most features are pure `tracegraph-core`; durable checkpoints live in `tracegraph-runtime`.

## Contents

- [Retries](#retries)
- [Async nodes](#async-nodes)
- [Parallel fan-out](#parallel-fan-out)
- [Send / dynamic fan-out](#send--dynamic-fan-out)
- [Checkpointing and resume](#checkpointing-and-resume)
- [Interrupts (human-in-the-loop)](#interrupts-human-in-the-loop)
- [Dynamic routing](#dynamic-routing)
- [Subgraphs](#subgraphs)
- [Streaming](#streaming)
- [Visualization](#visualization)

---

## Retries

Retry policy is **graph-definition, not runtime config**. Attach a `RetryPolicy` per node, or set a graph default. Per-node beats default.

```java
RetryPolicy policy = RetryPolicy.exponential(
        3,                       // max attempts
        Duration.ofMillis(100),  // base delay
        2.0,                     // multiplier
        Duration.ofSeconds(2));  // max delay

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("charge", chargeNode, policy)          // per-node
        .defaultRetryPolicy(RetryPolicy.fixed(2, Duration.ofMillis(50)))  // graph default
        .entry("charge").terminal("charge")
        .build();
```

- The executor handles backoff and emits `NodeListener.onRetry`.
- `Error` and `InterruptedException` **always short-circuit retries**.
- Use `ctx.idempotencyKey()` inside the node for your own dedup.
- Retries do **not** create extra trace steps — `TraceStep.attempts` records the count.

`RetryPolicy.fixed(...)` and `RetryPolicy.exponential(...)` are the built-in strategies.

## Async nodes

`AsyncNode<S>` returns `CompletableFuture<S>` and integrates with retry/checkpoint identically to a sync node.

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        .asyncNode("score", (state, ctx) ->
                CompletableFuture.supplyAsync(() -> score(state)))
        .entry("score").terminal("score")
        .build();
```

## Parallel fan-out

`parallel(name, branches, merger)` runs branches concurrently on the configured executor.

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        .parallel("enrich",
                List.of(
                        (s, ctx) -> withCustomerProfile(s),
                        (s, ctx) -> withFraudCheck(s),
                        (s, ctx) -> withInventory(s)),
                (input, branchResults) -> {
                    OrderState merged = input;
                    for (OrderState branch : branchResults) {
                        merged = merged.merge(branch);
                    }
                    return merged;
                })
        .entry("enrich").terminal("enrich")
        .build();
```

Branch contract (Phase 2c):

- Branches are **anonymous** — no names, no path entries, no listener events. They are invisible to listeners.
- All branches receive the **same input state** and merge in **declaration order**.
- **First-by-declaration-order failure wins.**
- `parallelAsync(...)` is the `CompletableFuture` variant.

The default executor is **virtual-thread-per-task**, lazily created per `run`. A user-supplied executor via `.executor(...)` is **not** shut down by the graph.

## Send / dynamic fan-out

When the number/targets of parallel work are only known at runtime, a routing node can spawn them:

```java
NodeResult.sendAll(
        List.of(new Send<>("worker", payloadA), new Send<>("worker", payloadB)),
        merger,
        currentState);
```

`Send<S>(target, payload)` is a lightweight record. The executor expands `SendAll` identically to `parallel(...)` but with runtime-determined targets and payloads.

## Checkpointing and resume

Wire a `CheckpointStore` and resume later by execution ID:

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        /* ... */
        .checkpointStore(new InMemoryCheckpointStore())   // tracegraph-runtime
        .build();

Optional<ExecutionResult<OrderState>> resumed = graph.resume("execution-123");
```

Semantics:

- **Checkpoints write after node exit, before edge resolution.** Resume re-evaluates outgoing edges of the saved `lastCompletedNode` and continues.
- Nodes are **at-least-once on resume** — a mid-node crash re-runs that node from attempt 1.
- The default `CheckpointStore` is **no-op**; opt in explicitly.

`tracegraph-runtime` ships:

- **`InMemoryCheckpointStore`** — for tests and single-process flows.
- **`JdbcCheckpointStore`** — durable, single table (default `tracegraph_checkpoint`), portable UPDATE-then-INSERT upsert in a transaction, idempotent `initSchema()`. Constructed via `JdbcCheckpointStore.of(dataSource, mapper, stateType[, table])`. Jackson is an optional dependency.

## Interrupts (human-in-the-loop)

Pause a run for human approval, then resume:

```java
Graph<S> graph = Graph.<S>builder()
        /* ... */
        .interruptBefore("approve")   // or .interruptAfter("review")
        .checkpointStore(store)
        .build();

ExecutionResult<S> r = graph.run(seed);   // status == INTERRUPTED
// ... human approves ...
graph.resume(r.executionId());            // continues
```

- `interruptBefore` writes a checkpoint with `interruptPending=true`; `interruptAfter` writes a normal `lastCompletedNode`. Both return `Status.INTERRUPTED`.
- Per-branch interrupts inside `parallel(...)` are **not** supported.
- The Spring Boot starter exposes `POST /tracegraph/traces/{id}/resume` — see **[[REST API Reference]]**.

## Dynamic routing

`RoutingNode<S>` chooses the next node at runtime instead of relying on edges:

```java
Graph<S> graph = Graph.<S>builder()
        .routingNode("router", (state, ctx) ->
                state.needsReview()
                        ? NodeResult.goTo("review", state)   // bypass edges
                        : NodeResult.of(state))              // fall through to normal edges
        /* ... */
        .build();
```

- `NodeResult.goTo(name, state)` bypasses edge resolution and jumps to `name`.
- `NodeResult.of(state)` falls through to normal edge resolution.
- An unknown `goTo` target throws `NodeExecutionException`.

## Subgraphs

Embed a compiled graph as a single node:

```java
Graph<S> parent = Graph.<S>builder()
        .subgraph("inner", innerGraph)   // both graphs share state type <S>
        /* ... */
        .build();
```

- Both graphs must share state type `<S>`.
- The trace records **one parent step** with `children` populated by the inner trace (requires compatible `TraceRecorder` setup).
- Resuming a parent into mid-subgraph is **not** supported — the subgraph re-runs from its start.

## Streaming

`Graph.stream(initial)` returns a `Flow.Publisher<NodeEvent<S>>`:

```java
graph.stream(initial).subscribe(subscriber);
// events: NodeEnter / NodeExit / NodeRetry / Failed / Complete
```

- Backed by a `SubmissionPublisher` (default buffer 256); the producer blocks when the buffer is full — **no events are dropped**, and a durable record also lives in the `TraceStore`.
- Core stays JDK-only.
- The Spring Boot starter exposes this as SSE at `POST /tracegraph/traces/stream` — see **[[REST API Reference]]**.

> Streaming here is **graph-level events** (node enter/exit/etc.), distinct from **LLM token streaming** (`LlmClient.stream(...)`) — see **[[LLM Connectors]]**.

## Visualization

Pure structural renders, no new dependencies:

```java
String mermaid  = graph.toMermaid();
String plantUml = graph.toPlantUml();
```

Subgraphs render as `subgraph` / `package` clusters.

---

**Related:** **[[Memory]]** · **[[Observability and Replay]]** · **[[Execution Model]]**
