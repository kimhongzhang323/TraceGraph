# Changelog

All notable changes to TraceGraph are recorded here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project follows [Semantic Versioning](https://semver.org/) — strictly once we hit 1.0.0, with documented breaks allowed in `0.x` minors.

## [Unreleased]

## [0.2.0] - 2026-05-04

### Breaking
- `TraceStep<S>` gained a trailing `children` record component for subgraph support. Positional constructor consumers must update; `TraceStep.leaf(...)` is the convenience factory for leaf steps.
- All Maven `artifactId`s renamed from `langgraph-*` to `tracegraph-*` (e.g. `tracegraph-core`, `tracegraph-runtime`). Update your dependency coordinates.

### Added
- **Streaming:** `Graph.stream(initial)` returns `Flow.Publisher<NodeEvent<S>>` with `NodeEnter`/`NodeExit`/`NodeRetry`/`Failed`/`Complete` events. Backed by a `SubmissionPublisher`; core stays JDK-only.
- **HITL interrupts:** `Builder.interruptBefore/After(name...)`; `Status.INTERRUPTED`; `POST /tracegraph/traces/{id}/resume` REST endpoint (404 unknown; 409 not INTERRUPTED).
- **Subgraphs:** `Builder.subgraph(name, Graph<S>)` embeds a compiled inner graph as a single node. `TraceStep.children` field carries the inner trace steps.
- **Dynamic routing:** `RoutingNode<S>` + `NodeResult.goTo(name, state)` to bypass edges at runtime; unknown target throws `NodeExecutionException`.
- **Visualization:** `Graph.toMermaid()` / `Graph.toPlantUml()` render the static graph structure; subgraphs appear as clusters.
- **Starter SSE:** `POST /tracegraph/traces/stream` endpoint on `TraceStreamController` registered when a single `Graph<?>` bean is present.
- **Spring Boot auto-config for `LlmClient`.** `LlmAutoConfiguration` wires `OpenAiLlmClient` or `AnthropicLlmClient` from `tracegraph.llm.*` properties. Provider: `tracegraph.llm.provider=openai|anthropic`; toggle with `tracegraph.llm.enabled=false`. User-defined `LlmClient` beans win.
- **`POST /tracegraph/traces/{id}/replay`** — re-execute a saved trace from a chosen step. `step` query param, default `-1` (replay from entry). 404 for unknown trace; 400 for out-of-range step.
- **`MemoryStore.pagedKeys(scope, offset, limit)`** — paginate keys without materializing the full set. Backwards-compatible default method; `JdbcMemoryStore` overrides with a backend `LIMIT/OFFSET` query.
- **Spring Boot auto-config for `JdbcMemoryStore`.** `MemoryAutoConfiguration` auto-wires `JdbcMemoryStore` when a `DataSource` and Jackson are present. Properties: `tracegraph.memory.jdbc.enabled`, `tracegraph.memory.jdbc.init-schema`, `tracegraph.memory.jdbc.table`.
- **Tool-use / ReAct.** `Tool`, `ToolDefinition`, `ToolCall`, `ToolResult` records in `tracegraph-connectors`. `ReActAgent<S>` factory builds a `Graph<S>` implementing the Reason+Act loop (`llm` → `tools` → `llm`, terminating at `done`).
- **Send / dynamic fan-out.** `NodeResult.sendAll(List<Send<S>>, merger, currentState)` spawns N parallel executions with runtime-determined targets and payloads, merged identically to `parallel(...)`.
- **Cost / token tracking.** `NodeListener.onUsage(nodeName, promptTokens, completionTokens)` fires after any `ChatNode` call. `LlmCostListener` accumulates totals per-execution and per-node. `OtelNodeListener` emits `llm.usage.*` span attributes. `TraceStep.Usage` records per-step usage.
- **`LlmClient.stream(LlmRequest)`** returns `Flow.Publisher<LlmStreamChunk>` with incremental token deltas; default wraps `complete()` into a single-chunk publisher.

### Build
- **Release from CI.** `Release` GitHub Actions workflow publishes to Maven Central on tag push (`v*`). Requires `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY`, `MAVEN_GPG_PASSPHRASE` secrets. See `RELEASING.md`.

## [0.1.0] - 2026-04-28

### Build
- **Maven groupId is `site.tracegraph`** (reverse-DNS of the verified `tracegraph.site` Sonatype namespace). Java package names — `io.tracegraph.core.*`, etc. — are independent of the Maven groupId and remain unchanged.
- Maven Central publishing scaffold: `release` Maven profile attaches sources + javadoc jars, signs with GPG, and uploads via `central-publishing-maven-plugin`. Parent POM now declares `<licenses>`, `<developers>`, `<scm>`, `<url>`, `<issueManagement>`. See `RELEASING.md` for the publish runbook.

### Added
- **`JdbcMemoryStore`** (`tracegraph-memory`) — durable scoped key-value memory backed by any JDBC `DataSource`. Single table (default `tracegraph_memory`) with composite `(scope, key_name)` PK, `value_json` blob, and `updated_at` timestamp. Portable UPDATE-then-INSERT upsert in a transaction; idempotent `initSchema()`; configurable table name. Uses Jackson default-typing-as-property to round-trip heterogeneous values, matching `FileMemoryStore`. Persistence failures surface as `MemoryPersistenceException`.
- **`JdbcTraceStore`** (`tracegraph-observability`) — durable trace persistence backed by any JDBC `DataSource`. Single table (default `tracegraph_trace`), denormalized columns (`execution_id`, `status`, `started_at`, `completed_at`, fork lineage) plus a JSON blob carrying the full DTO. Portable UPDATE-then-INSERT upsert in a transaction; `listIds()` returns `ORDER BY started_at`; idempotent `initSchema()`. Constructed via `JdbcTraceStore.of(dataSource, stateType[, table])`. Persistence failures surface as `TracePersistenceException`. Round-trip preserves fork lineage and lossy-`Throwable` semantics.
- **`JdbcCheckpointStore`** (`tracegraph-runtime`) — durable checkpoints backed by any JDBC `DataSource`. Single table (default `tracegraph_checkpoint`), portable upsert via UPDATE-then-INSERT in a transaction, idempotent `initSchema()`, configurable table name. Constructed via `JdbcCheckpointStore.of(dataSource, mapper, stateType[, table])`. Jackson is an `<optional>` dep; persistence failures surface as `CheckpointPersistenceException`.
- **Spring Boot starter** — `TraceGraphAutoConfiguration` registers no-op beans for the four SPIs (`NodeListener`, `CheckpointStore`, `TraceRecorder`, `MemoryStore`), each `@ConditionalOnMissingBean`. `TraceWebAutoConfiguration` registers `TraceController` exposing:
  - `GET /tracegraph/traces?limit&offset` — list executionIds with `X-Total-Count` header
  - `GET /tracegraph/traces/{id}` — full trace JSON (404 if unknown)
  - `DELETE /tracegraph/traces/{id}` — remove a trace (204 / 404)
  - `GET /tracegraph/traces/{a}/diff/{b}` — diff two traces (404 if either missing)
  - `TraceGraphProperties` (`prefix=tracegraph`) with `tracegraph.web.enabled` toggle
- **Connectors** — vendor-neutral `LlmClient` SPI (`LlmRequest`/`LlmResponse`/`ChatMessage`), `MockLlmClient`, `OpenAiLlmClient` (HTTP), `AnthropicLlmClient` (Messages API), `ChatNode<S>` adapter. `LlmHttpException` surfaces non-2xx responses.
- **Memory** — `MemoryStore` SPI in core, `InMemoryMemoryStore`, `FileMemoryStore` (atomic writes, path-traversal guard, Jackson default-typing for heterogeneous values).
- **Observability**
  - `NodeListener` SPI in core; `OtelNodeListener` (one span per node, retries as events, errors set `StatusCode.ERROR`); `Listeners.compose(...)`; `StateRenderer` for state diffs as span events.
  - `TraceRecorder` SPI in core (executionId-aware, separate from `NodeListener`); `ExecutionTrace<S>`/`TraceStep<S>`; `RecordingTraceRecorder`; `Replayer`; `InMemoryTraceStore`; `JsonFileTraceStore` (atomic writes, JavaTime support).
  - `TraceDiff.between(left, right)` — longest common prefix, divergence index, per-side remainders, `identical()`.
  - `ReplayRunner.of(parent, graph).reRunFrom(stepIndex[, seedOverride])` — re-execute a saved trace against a (possibly modified) graph; `forkedFromExecutionId` + `forkedFromStepIndex` lineage on the new trace.
- **Runtime**
  - Per-node and graph-default `RetryPolicy` with `BackoffStrategy`; `Error` and `InterruptedException` short-circuit retries.
  - `CheckpointStore` SPI; checkpoints write after node exit, before edge resolution; resume re-evaluates outgoing edges.
  - `AsyncNode<S>` returning `CompletableFuture<S>`; `parallel(name, branches, merger)` with first-by-declaration-order failure semantics. Default executor is virtual-thread-per-task, lazily created per `run` and shut down on completion.
- **Core** — typed `Graph<S>`/`Node<S>`/`Edge<S>`, fluent builder with eager validation, sync execution loop, `ExecutionResult<S>`, conditional edges, `runFrom(node, seed, executionId)` entry point.

### Conventions
- JDK 21, Maven multi-module, JUnit 5 + AssertJ, `-Xlint:all -Werror`.
- `tracegraph-core` stays SLF4J-only; Spring / Jackson / OTel live in downstream modules.
- Jackson is `<optional>` everywhere it appears; consumers opt in by adding it.
