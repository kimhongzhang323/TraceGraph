# Changelog

All notable changes to TraceGraph are recorded here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project follows [Semantic Versioning](https://semver.org/) — strictly once we hit 1.0.0, with documented breaks allowed in `0.x` minors.

## [Unreleased]

### Build
- Maven Central publishing scaffold: `release` Maven profile attaches sources + javadoc jars, signs with GPG, and uploads via `central-publishing-maven-plugin`. Parent POM now declares `<licenses>`, `<developers>`, `<scm>`, `<url>`, `<issueManagement>`. See `RELEASING.md` for the publish runbook.

### Added
- **`JdbcCheckpointStore`** (`langgraph-runtime`) — durable checkpoints backed by any JDBC `DataSource`. Single table (default `tracegraph_checkpoint`), portable upsert via UPDATE-then-INSERT in a transaction, idempotent `initSchema()`, configurable table name. Constructed via `JdbcCheckpointStore.of(dataSource, mapper, stateType[, table])`. Jackson is an `<optional>` dep; persistence failures surface as `CheckpointPersistenceException`.

### Added
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
- `langgraph-core` stays SLF4J-only; Spring / Jackson / OTel live in downstream modules.
- Jackson is `<optional>` everywhere it appears; consumers opt in by adding it.
