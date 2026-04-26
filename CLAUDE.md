# TraceGraph — AI context

## Product thesis

**A production-grade agent runtime for the JVM.** Typed graphs, durable memory, deep observability. NOT "LangGraph but Java." Every change should reinforce reliability, debuggability, enterprise readiness.

## Stack

- **JDK 21** (records, pattern matching, virtual threads)
- **Maven** multi-module — parent POM at root, 6 modules
- **JUnit 5** + **AssertJ** for tests
- **SLF4J** API only at runtime in `langgraph-core` — no Spring, Jackson, OTel in core

## Modules and what belongs where

| Module | Belongs here |
|---|---|
| `langgraph-core` | Pure graph definitions and the sync execution loop. Zero heavy deps. The `NodeListener` SPI lives here so Phase 3 can plug in without a breaking change. |
| `langgraph-runtime` | Async/parallel nodes, checkpointing, retries, resume |
| `langgraph-observability` | OTel spans, state diffs, replay |
| `langgraph-memory` | `MemoryStore` interface + working/session/long-term/semantic layers |
| `langgraph-spring-boot-starter` | Auto-config, REST endpoints, DI |
| `langgraph-connectors` | LLM + vector DB adapters |

If a feature would force `langgraph-core` to depend on Spring / Jackson / OTel / a memory store — it goes in another module. Core stays minimal.

## Conventions

- **Single type parameter `<S>` on `Node`/`Graph`.** Use state composition for sub-results, not `<S, R>`. The fluent builder is the contract — don't break it.
- **Edges are first-class data** (record, top-level). They will be enumerated by replay/visualization later.
- **Immutable state preferred.** Nodes return the next state; don't mutate.
- **No comments unless the WHY is non-obvious.** Identifier names should carry the WHAT.
- **Compiler args:** `-Xlint:all -Werror`. Warnings break the build.
- **Tests cover behavior, not implementation.** Validation tests, execution tests, listener tests — see `langgraph-core/src/test/java/`.
- **Async + parallel.** `AsyncNode<S>` returns `CompletableFuture<S>`; integrates with retry/checkpoint identically to sync. `parallel(name, branches, merger)` runs branches concurrently on the configured executor — branches are anonymous (no names, no path entries, no listener events), receive the same input state, and merge in declaration order. First-by-declaration-order failure wins. Default executor is virtual-thread-per-task, lazily created per `run` and shut down on completion; user-supplied executors via `.executor(...)` are NOT shut down by the graph.
- **Checkpoints write after node exit, before edge resolution.** Resume re-evaluates outgoing edges of the saved `lastCompletedNode` and continues from there. Nodes are at-least-once on resume — if a crash happens mid-node, that node re-runs from attempt 1. Edge predicates must be pure functions of state. The default `CheckpointStore` is no-op; opt in via `.checkpointStore(...)`.
- **Retry policy is graph-definition, not runtime config.** `RetryPolicy` attaches to a node via `.node(name, fn, policy)` or to the whole graph via `.defaultRetryPolicy(policy)`. Per-node beats default. The executor handles backoff and emits `NodeListener.onRetry`; node code uses `ctx.idempotencyKey()` for its own dedup. `Error` and `InterruptedException` always short-circuit retries.

## Build commands

```bash
mvn test                              # Run all tests
mvn -pl langgraph-core test           # Test one module
mvn -B install -DskipTests            # Install all artifacts to local repo
mvn clean                             # Clear target/
```

JDK 21 is required for the build. If `mvn` runs under JDK 17, set `JAVA_HOME` to a 21 install.

## What NOT to do

- Don't add `MemoryStore` to `langgraph-core` — Phase 4 designs it from real requirements.
- Don't add Spring imports anywhere except `langgraph-spring-boot-starter`.
- Don't pull in OTel from `langgraph-core` — wire it through `NodeListener` in `langgraph-observability`.
- Don't change `Node<S>` to `Node<S, R>`. State composition handles sub-results.
- Don't add backwards-compat shims, dead code, or "in case we need it later" abstractions.

## Strategy doc

The roadmap (Phase 1 → Phase 7) and product positioning are tracked in conversation context, not committed. Killer differentiator: **replay any agent execution with full state diff + reasoning trace.** Every Phase 1–3 design choice should preserve that future.
