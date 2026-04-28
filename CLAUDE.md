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
- **Observability via NodeListener.** OTel tracing lives in `langgraph-observability` as `OtelNodeListener`, plugged in via `Graph.Builder.listener(...)`. One span per node; retries are span events on the same span (no span-per-attempt); errors set `StatusCode.ERROR` and call `Span.recordException`. Branches inside `parallel(...)` don't get spans (Phase 2c contract — branches are invisible to listener). Compose listeners via `Listeners.compose(...)`. Core stays OTel-free. State diffs flow through `NodeListener.onState(name, before, after)`, fired once per successful node exit (not on failure, not per retry); OTel binds them as a `state` span event with rendered before/after attributes; renderer is pluggable via `StateRenderer` (default `String::valueOf`).
- **Async + parallel.** `AsyncNode<S>` returns `CompletableFuture<S>`; integrates with retry/checkpoint identically to sync. `parallel(name, branches, merger)` runs branches concurrently on the configured executor — branches are anonymous (no names, no path entries, no listener events), receive the same input state, and merge in declaration order. First-by-declaration-order failure wins. Default executor is virtual-thread-per-task, lazily created per `run` and shut down on completion; user-supplied executors via `.executor(...)` are NOT shut down by the graph.
- **Checkpoints write after node exit, before edge resolution.** Resume re-evaluates outgoing edges of the saved `lastCompletedNode` and continues from there. Nodes are at-least-once on resume — if a crash happens mid-node, that node re-runs from attempt 1. Edge predicates must be pure functions of state. The default `CheckpointStore` is no-op; opt in via `.checkpointStore(...)`. `langgraph-runtime` ships `InMemoryCheckpointStore` and `JdbcCheckpointStore` (single table, portable UPDATE-then-INSERT upsert in a transaction, idempotent `initSchema()`, Jackson `<optional>`).
- **Replay foundation.** The `TraceRecorder` SPI in core is executionId-aware (separate from `NodeListener`, which is span-shaped and executionId-blind by design); plug in via `Graph.Builder.traceRecorder(...)`. `ExecutionTrace<S>`/`TraceStep<S>` records, `TraceStore` SPI, `InMemoryTraceStore`, `RecordingTraceRecorder`, and `Replayer` live in `langgraph-observability` under `replay/`. One trace per executionId — resume appends to the prior trace by loading from the `TraceStore` and seeding the in-flight builder. Branches inside `parallel(...)` produce one step (Phase 2c contract). Retries don't create extra steps; `TraceStep.attempts` records the count.
- **Persistent traces.** `JsonFileTraceStore<S>` (`langgraph-observability`, `replay/`) writes one JSON file per trace under a directory, keyed by executionId. Constructed via `JsonFileTraceStore.of(dir, stateType)` — the `Class<S>` is required to deserialize state values back to their concrete type. Uses Jackson with `JavaTimeModule` (Jackson is an `<optional>` dep of observability — pulled in only by users who opt into the file store). Writes go through a `*.tmp` sibling + `ATOMIC_MOVE` for crash safety. Path traversal guard rejects `executionId` containing `/`, `\`, or `..`. Throwable round-trip is lossy: only `className` + `message` are stored, reconstituted as a plain `RuntimeException("[className] message")` on load — replay UX cares about the failure point, not stack traces. Complements `InMemoryTraceStore` for tests; future `JdbcTraceStore` is a separate slice.
- **Trace diffing.** `TraceDiff.between(left, right)` walks two `ExecutionTrace<S>` step-by-step and surfaces a longest common prefix (matched by nodeName + before/after equality), the divergence index, and per-side remainders. `sameStatus`/`sameFinalState` flags compare the run-level outcomes. `identical()` is a convenience for "no divergence + same status + same final state". Pure-data record in `langgraph-observability/replay/`; no executor or store coupling.
- **Replay re-execution.** `ReplayRunner.of(parent, graph).reRunFrom(stepIndex[, seedOverride])` re-executes a saved trace from a chosen step against a (possibly modified) graph. `stepIndex == -1` means "from entry"; default seed is `parent.steps[stepIndex].before()`. Each fork gets a fresh executionId; the new `ExecutionTrace` carries `forkedFromExecutionId` + `forkedFromStepIndex` to record lineage. Mechanic: `Graph.runFrom(startNode, seed, executionId)` is the third executor entry point alongside `run`/`resume`, with no `CheckpointStore` interaction. No determinism guarantee — nodes own their own determinism (LLM, HTTP, side effects). Branch-level fork inside `parallel(...)` and trace diffing are out of scope.
- **Memory.** `MemoryStore` SPI in core (`io.tracegraph.core.spi.MemoryStore`) is a key-value store with explicit `scope` per call (`get/put/delete/keys`). Wired in via `Graph.Builder.memoryStore(...)`; default is `MemoryStore.noop()`. Nodes access it through `ctx.memory()` (default no-op on `Context` so existing impls aren't broken). Working memory is the state object itself — the store is for cross-execution data. `langgraph-memory` ships `InMemoryMemoryStore` (`ConcurrentHashMap` per scope) and `FileMemoryStore` (one JSON file per `{scope}/{key}` under a root directory; Jackson default-typing-as-property so heterogeneous values round-trip; atomic `*.tmp` + `ATOMIC_MOVE`; path-traversal guard on scope and key; Jackson is an `<optional>` dep). TTL/expiry, vector/semantic search, and persistent JDBC stores are deferred slices that build on this substrate.
- **LLM connector SPI.** `langgraph-connectors` ships a minimal vendor-neutral `LlmClient` interface (`complete(LlmRequest) → LlmResponse`). `LlmRequest`/`LlmResponse`/`ChatMessage` are records covering the lowest common denominator across chat-LLM APIs (messages, model, temperature, maxTokens; usage tokens; finish reason). `MockLlmClient` (echoing/constant/lambda) is the in-tree test double. `OpenAiLlmClient` is a real HTTP adapter for OpenAI-compatible chat-completions endpoints (built on JDK `HttpClient` + Jackson; Jackson is `<optional>`); endpoint, API key, custom `HttpClient`, and request timeout are configured via `OpenAiLlmClient.builder()`. `AnthropicLlmClient` adapts the Anthropic Messages API (`POST /v1/messages`, `x-api-key` + `anthropic-version` headers, system messages lifted into a top-level `system` field, content blocks concatenated on response). Non-2xx responses from either adapter surface as `LlmHttpException(statusCode, body)`. `ChatNode<S>` adapts any `LlmClient` to a `Node<S>` via user-provided `requestBuilder` and `responseFolder` functions — keeps the bridge between LLM and graph state explicit. Streaming, tool use, embeddings, and real HTTP adapters (OpenAI, Anthropic, etc.) are per-vendor follow-up slices that build on this contract.
- **Spring Boot starter.** `langgraph-spring-boot-starter` ships `TraceGraphAutoConfiguration` that registers no-op beans for the four SPIs (`NodeListener`, `CheckpointStore`, `TraceRecorder`, `MemoryStore`), each guarded by `@ConditionalOnMissingBean` so user beans win. Auto-config is wired via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3 style). `Graph<?>` is NOT auto-registered — it's generic in `S` so the user defines their own `@Bean Graph<TheirState>` and injects the SPI beans. A second auto-config (`TraceWebAutoConfiguration`, package `boot.web`) registers a `TraceController` exposing `GET /tracegraph/traces` (list executionIds; optional `?limit=N&offset=M` pagination, `X-Total-Count` response header carries the unpaginated total; 400 on negative values) and `GET /tracegraph/traces/{id}` (full trace JSON; 404 if unknown), `GET /tracegraph/traces/{a}/diff/{b}` (returns `TraceDiff` JSON; 404 if either id is unknown), and `DELETE /tracegraph/traces/{id}` (204 on success, 404 if unknown). `TraceStore.listIds()` is a default method returning `List.of()` so existing impls keep compiling; `InMemoryTraceStore` and `JsonFileTraceStore` override it. — guarded by `@ConditionalOnClass(DispatcherServlet, TraceStore)` + `@ConditionalOnWebApplication` + `@ConditionalOnBean(TraceStore)`, so non-web apps and starter consumers without the observability module are unaffected. `langgraph-observability` and `spring-webmvc` are `<optional>` deps. `TraceGraphProperties` (`prefix=tracegraph`) exposes `tracegraph.web.enabled` (default `true`) — set it to `false` to suppress the `TraceController` bean (`@ConditionalOnProperty` on `TraceWebAutoConfiguration`). An `@EnableTraceGraph` annotation is a deferred slice. Spring Boot version pinned to 3.3.5 in the parent BOM.
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

- Don't add memory implementations (JDBC, Redis, vector) to `langgraph-core` — only the `MemoryStore` SPI lives there. Impls go in `langgraph-memory` (or a future connector module).
- Don't add Spring imports anywhere except `langgraph-spring-boot-starter`. The starter depends only on `spring-boot-autoconfigure`; resist adding `spring-web`/`spring-jdbc`/etc. unless the slice actually needs them.
- Don't pull in OTel from `langgraph-core` — wire it through `NodeListener` in `langgraph-observability`.
- Don't change `Node<S>` to `Node<S, R>`. State composition handles sub-results.
- Don't add backwards-compat shims, dead code, or "in case we need it later" abstractions.

## Strategy doc

The roadmap (Phase 1 → Phase 7) and product positioning are tracked in conversation context, not committed. Killer differentiator: **replay any agent execution with full state diff + reasoning trace.** Every Phase 1–3 design choice should preserve that future.
