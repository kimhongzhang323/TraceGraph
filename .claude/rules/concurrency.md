# Concurrency rules

TraceGraph's executor is sync today (Phase 1) but Phase 2 introduces async/parallel nodes. Every concurrency choice should be made with virtual threads (JDK 21) in mind.

## Thread-safety expectations

| Type | Contract |
|---|---|
| `Graph<S>` | Immutable after `build()`. Safe to share across threads. Multiple `run` calls in parallel must not corrupt graph state. |
| `Graph.Builder<S>` | NOT thread-safe. Single-thread construction. |
| `Node<S>` | Implementations must be thread-safe if reused across executions. Stateless nodes are the norm. |
| `Edge<S>` | Records are immutable; predicates must be pure / thread-safe. |
| `Context` | Per-execution, per-node. Never shared across executions. |
| `ExecutionResult<S>` | Immutable record. |
| `NodeListener` | Implementations must be thread-safe. Listener calls in Phase 2 may come from multiple worker threads. |
| `MemoryStore` (Phase 4) | Implementations must be thread-safe. |

## Virtual threads

- Target Loom. Every blocking call (HTTP, JDBC, file I/O) inside a node should be virtual-thread friendly.
- **No `synchronized` blocks across blocking I/O.** Pinning carrier threads kills throughput. Use `ReentrantLock` instead.
- Don't `ThreadLocal` cache anything in node execution paths — VTs disrupt TL semantics.

## Memory model

- Use `final` aggressively. Final fields publish safely.
- For mutable shared state in non-core modules: prefer `AtomicReference` over `volatile` + manual synchronization.
- Use immutable collections (`List.copyOf`, `Map.copyOf`) at module boundaries.

## What to avoid

- `synchronized(someLock) { httpClient.send(...) }` — pin
- `ThreadLocal` for per-execution context — use `Context` parameter
- `Thread.sleep` in nodes — kills throughput, blocks the carrier
- Spawning unbounded executors — runtime owns executor lifecycle
- Sharing a `Graph.Builder` across threads — undefined behavior

## Phase 2 preview

The async runtime will run nodes on a virtual-thread `ExecutorService`. Parallel branches must:
- Not share mutable state
- Have a deterministic merge step (defined by the graph, not the runtime)
- Surface failures of any branch to the parent execution
