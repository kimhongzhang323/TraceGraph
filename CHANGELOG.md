# Changelog

All notable changes to TraceGraph are recorded here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project follows [Semantic Versioning](https://semver.org/) — strictly once we hit 1.0.0, with documented breaks allowed in `0.x` minors.

## [Unreleased]

### Added
- **`CircuitBreakerLlmClient`** (`tracegraph-connectors`): closed/open/half-open breaker over any `LlmClient` — opens after N consecutive failures, rejects fast with `CircuitOpenException` (carrying the last failure as cause) while open, half-opens after the cooldown with exactly one probe (success closes, failure re-opens and restarts the cooldown; concurrent calls during the probe are rejected). State transitions are lock-guarded; delegate calls happen outside the lock.
- **`RateLimitedLlmClient`** (`tracegraph-connectors`): token-bucket rate-limiting decorator over any `LlmClient` — `of(delegate, permits, per)` allows bursts up to `permits` and refills continuously. Blocking is virtual-thread friendly (`ReentrantLock`/`Condition`, no synchronized-over-I/O, no busy-spin); interruption while waiting propagates with the interrupt flag restored. Share one instance across everything bound by the same provider quota.
- **Native Gemini streaming** (`tracegraph-connectors`): `GeminiLlmClient` now overrides `stream(LlmRequest)` with real SSE via `:streamGenerateContent?alt=sse` — text parts become content chunks; `functionCall` parts (which Gemini sends whole) emit one `ToolCallDelta` each and force the terminal finish reason to `TOOL_CALLS`; `SAFETY`/`PROHIBITED_CONTENT`/`BLOCKLIST` map to `REFUSED`. Non-2xx surfaces as `LlmHttpException`; malformed SSE chunks are skipped.
- **Native Anthropic streaming** (`tracegraph-connectors`): `AnthropicLlmClient` now overrides `stream(LlmRequest)` with real SSE (`stream: true` on the Messages API) instead of the single-chunk fallback — `content_block_delta`/`text_delta` events become content chunks, `tool_use` blocks stream as `ToolCallDelta`s (name from `content_block_start`, arguments accumulated from `input_json_delta`), and `message_delta.stop_reason` maps to the terminal chunk's finish reason (`refusal` → `REFUSED`). Non-2xx surfaces as `LlmHttpException`; malformed SSE chunks are skipped without killing the stream.
- **`ChatSession`** (`tracegraph-connectors`): durable conversation history layered on the `MemoryStore` SPI — `append(ChatMessage)` / `messages()` / `clear()` keyed by `scope` + `sessionId` (default scope `tracegraph.session`). History reloads across executions and processes when backed by a persistent store (`FileMemoryStore`/`JdbcMemoryStore`); single-instance appends are lock-guarded, tool-call and multimodal messages round-trip intact.
- **`FallbackLlmClient`** (`tracegraph-connectors`): provider failover over an ordered list of `LlmClient`s — falls through on transient failures (HTTP 408/429/5xx, network errors/timeouts), propagates non-retryable errors (e.g. 401) and interruptions immediately, and surfaces which provider answered via the existing `servedModel`/`rerouted()` mechanism. Custom failover predicate via `FallbackLlmClient.of(clients, predicate)`; when all clients fail, the last failure carries earlier ones as suppressed exceptions.
- **API-compatibility gate**: `japicmp-maven-plugin` bound to `verify`, comparing every published module against the `0.4.0` baseline and failing the build on binary-incompatible changes. Intentional pre-1.0 breaks require an explicit exclusion plus a CHANGELOG entry. Non-published modules (`bench`, `e2e`, `demo`) skip the check.
- `scripts/verify.sh` — one-shot local verification gate (JDK 21 selection + `mvn verify`); `docs/0.5.0-PROGRESS.md` tracks the 0.5.0 work queue.

### Fixed
- **`RecordedLlmClient` multimodal round-trip**: `ContentBlock` now carries Jackson polymorphic type info (`@JsonTypeInfo`/`@JsonSubTypes`, `type` = `text`/`image`), so exchanges containing content blocks survive `save(Path)`/`load(Path)` and replay by `LlmRequest` equality. Previously `load` threw on any recording with multimodal messages. Jackson remains an optional dependency.
- `release.yml` now strips the `v` prefix from the tag before collecting release artifacts (GitHub Release assets were empty since v0.3.0) and groups the `find` predicates so `.pom` files are collected too.

## [0.4.0] - 2026-06-11

### Removed
- `io.tracegraph.connectors.vector.InMemoryVectorStore`, `io.tracegraph.connectors.llm.MockEmbeddingClient`, and `io.tracegraph.connectors.llm.OpenAiEmbeddingClient` — duplicates of the canonical `io.tracegraph.rag.*` implementations, deprecated `forRemoval` during the 0.4.0 development cycle and removed before ever shipping in a release. Migrate imports to `io.tracegraph.rag.InMemoryVectorStore` / `MockEmbeddingClient` / `OpenAiEmbeddingClient` (drop-in equivalents).

### Added
- **SPI contract tests**: abstract `MemoryStoreContractTest`, `CheckpointStoreContractTest`, and `TraceStoreContractTest` base classes; every store implementation now extends the shared behavioral contract for its SPI.
- **Recorded-LLM replay stubbing** (`tracegraph-connectors`): `RecordedLlmClient.recording(delegate)` captures full request/response exchanges during a live run (persist via `save(Path)` / `load(Path)`, Jackson optional); `RecordedLlmClient.replaying(exchanges[, fallback])` serves them back matched by `LlmRequest` equality — unchanged fork steps get recorded answers deterministically, edited prompts throw `LlmReplayMismatchException` or fall back to a live client. Pairs with `ReplayRunner` for deterministic what-if forks.

- **Refusal + served-model capture**: `LlmResponse.FinishReason` gains `REFUSED` (OpenAI `content_filter`, Anthropic `refusal`, Gemini `SAFETY`/`PROHIBITED_CONTENT`/`BLOCKLIST`); `LlmResponse` gains a `servedModel` component (the model that actually answered) plus `rerouted(requestedModel)` to detect safeguard rerouting. `LlmCallInfo` gains `servedModel` + `rerouted()`; `ChatNode` reports it; `OtelNodeListener` emits `gen_ai.response.model`. Backwards-compatible constructors keep existing positional callers compiling.
- **Append-oriented trace flush** (`tracegraph-observability`): new `TraceStore.appendStep(snapshot, newStep)` default method (falls back to full `save`); `RecordingTraceRecorder`'s periodic flush now goes through it so append-friendly stores can persist only the new step instead of rewriting the whole trace every N steps.

### Changed
- **Shared HTTP plumbing**: package-private `JsonHttp` helpers in `tracegraph-connectors` (LLM adapters) and `tracegraph-rag` (vector stores + embedding clients) replace ten copies of the serialize/send/status-check/parse cycle. No public API change.
- **Record component additions** (pre-1.0 documented break): `LlmResponse` and `LlmCallInfo` gained a trailing `servedModel` component — positional deconstruction over the full component list needs updating; all prior constructors still compile.
- `CLAUDE.md` module table regenerated to cover all 14 reactor modules (was 6).

### Fixed
- **Gemini API keys no longer leak into URLs**: `GeminiLlmClient` and `GeminiEmbeddingClient` now send the key via the `x-goog-api-key` header instead of the `?key=` query parameter (query strings end up in logs and proxies).
- **`GeminiLlmClient` tool loop**: assistant tool calls are now rendered as `functionCall` parts and tool results as `functionResponse` parts (consecutive results coalesced into one user turn). Previously tool results were sent as plain user text, breaking ReAct loops on Gemini. Malformed tool-call arguments are preserved as `_malformed_arguments` instead of failing.
- **Vector-store scope injection**: `QdrantVectorStore` and `WeaviateVectorStore` reject scopes containing characters that could escape a URL path segment or the GraphQL document (`IllegalArgumentException`).
- **Silent metadata loss in `WeaviateVectorStore`**: corrupt `tgMeta` payloads now surface as `VectorStoreHttpException` instead of silently returning empty metadata; metadata serialization failures throw instead of writing `{}`.
- **Parse hardening**: `QdrantVectorStore` reports a missing `result` array with a clear message; `PineconeVectorStore` reports matches missing `id` instead of throwing `NullPointerException`.

## [0.3.0] - 2026-05-24

### Added
- **Multi-agent patterns** (`tracegraph-connectors/llm`):
  - `HandoffNode<S>` — peer-to-peer agent delegation via `RoutingNode<S>` + `handoffSelector`; `"continue"` falls through, unknown/null terminates at `"done"`.
  - `AgentProfile<S>` + `ReActAgent.Builder.profile(...)` — per-agent role prompt + tool isolation (replaces prior `tool(...)` registrations on the builder).
  - `GroupChatAgent<S>` — N-agent rotation with `SpeakerSelector.roundRobin()` or `SpeakerSelector.llm(...)`; halts on `terminationPredicate`.
  - `VotingNode<S>` — `parallel(...)` fan-out across candidate ReAct subgraphs with `Tally.majority(...)` / `Tally.firstNonNull(...)` aggregation.
- **Termination-predicate listener** (`tracegraph-observability`): `TerminationListener<S>` (`maxTurns`/`afterNode`/`stateMatches`) throws `TerminationSignalException`, surfaced by the executor as `Status.TERMINATED`.
- **Per-execution per-node cost breakdown** (`tracegraph-observability`): `LlmCostListener` now implements `TraceRecorder` so wiring it via both `.listener(...)` and `.traceRecorder(...)` captures executionId-aware usage. New `snapshot(executionId)` returns an immutable `CostReport(executionId, usageByNode, totalUsage)` for billing dashboards. New top-level `Usage` and `CostReport` records, plus query helpers `usageByNode(executionId)` / `totalUsage(executionId)`. The 4-arg `recordUsage(executionId, nodeName, ...)` deliberately skips the global per-node bucket — `onUsage` owns it — so the dual wiring doesn't double-count.
- **Multi-agent trace correlation** (`tracegraph-observability`): `ExecutionTrace` gained `parentExecutionId` / `parentStepIndex` components (mirrors the existing `forkedFromExecutionId` lineage). Subgraph child traces auto-populate this lineage via a new `TraceRecorder.recordChildOf(...)` SPI hook the executor calls before invoking the inner graph. `JsonFileTraceStore` / `JdbcTraceStore` round-trip the fields (JDBC schema adds `parent_execution_id` and `parent_step_index` columns). `OtlpTraceExporter` emits `tracegraph.parent.execution.id` / `tracegraph.parent.step.index` span attributes on child traces.
- **Observability batch**: `MicrometerNodeListener` (timer/counter bridge to `MeterRegistry`); `SamplingTraceStore` with `random(rate)`/`slowExecutions(thresholdMs)`/`failedOnly()`; OTel GenAI semantic-convention attributes (`gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.*`, `gen_ai.response.finish_reasons`); `Graph.Builder.correlationId(Supplier<String>)` propagated to `ExecutionTrace.correlationId` and OTLP span links; `SlowNodeListener` per-node SLA budgets; `JsonlTraceExporter` for batch ingestion into LangSmith/Langfuse/Arize; per-step `TraceStep.Usage(promptTokens, completionTokens)` populated from `NodeListener.onUsage`; `Graph.Builder.sensitiveDataLogging(boolean)` flag gating prompt/response capture in traces.
- **Eval-harness batch** (`tracegraph-eval`): `BleuMetric` / `RougeMetric` / `TokenF1Metric`; `EvalSuite.Builder.failFast(boolean)`/`minPassRate(double)` + `assertPassed(...)` for CI gating; `EvalBaseline` + `EvalBaselineStore` + `EvalReport.toComparisonMarkdown(...)` for run-over-run regression detection; `EvalCaseLoader.fromJsonl(...)` and `fromCsv(...)` dataset loaders; `EvalSuite.runParallel(Executor)` on virtual threads; `EvalReport.toSummaryMarkdown(...)` + `EvalSummary` with pass rate, per-metric means, latency p50/p95/p99; `Metric.canScore(EvalCase<S>)` default for conditional skipping; `TraceAssertion<S>` SAM + `EvalRunner.Builder.assertion(...)` for per-step golden-trace assertions.
- **A2A protocol** (`tracegraph-a2a`): `Agent<S>`, `AgentBus` SPI, `InMemoryAgentBus` with virtual-thread dispatch, `AgentMessage` record with `of()`/`reply()` factories, `AgentTimeoutException`.
- **A2A HTTP transport**: `A2AHttpClient`, `A2AMessage`, `A2AHttpException` for wire-compatible Google A2A JSON exchange.
- **Spring Boot A2A auto-config**: `A2AAutoConfiguration` registers `InMemoryAgentBus`; `A2AController` exposes `POST /a2a/messages`.
- **Eval module** (`tracegraph-eval`): `EvalCase`, `EvalSuite`, `EvalReport`, built-in metrics (`ExactMatch`, `Contains`, `Latency`), Markdown + JUnit XML reporters.
- **Guardrail SPI** (`tracegraph-core`): `Guardrail<T>` functional interface, `GuardrailVerdict` with ALLOW/BLOCK/TRANSFORM; `andThen()` composition.
- **Guardrail implementations** (`tracegraph-connectors`): `LengthGuardrail`, `RegexPiiGuardrail` (email/SSN/CC/phone), `JsonSchemaGuardrail`, `LlmRequestGuardrail`.
- **Prompt templates** (`tracegraph-connectors`): `PromptTemplate` record with Mustache-style `{{var}}` rendering and SHA-256 checksum; `PromptLibrary` for classpath/directory loading.
- **Structured output** (`tracegraph-connectors`): `StructuredOutput<T>` Jackson-backed extraction from `LlmResponse`.
- **Annotation-based tool binding** (`tracegraph-connectors`): `@ToolMethod` + `ToolMethodAdapter` — converts Java methods to `Tool`/`ToolDefinition` via reflection.
- **Multi-vendor embeddings** (`tracegraph-rag`): `OllamaEmbeddingClient`, `GeminiEmbeddingClient`, `CohereEmbeddingClient` joining the existing `OpenAiEmbeddingClient`.
- **Embedding auto-config**: `EmbeddingAutoConfiguration` in starter selects provider via `tracegraph.rag.embedding.provider` property.
- **Cost budget listener** (`tracegraph-observability`): `CostBudgetListener` with per-model pricing (`ModelPricing`) and configurable `budgetUsd`; throws `BudgetExceededException` on overrun.
- **LLM cassette/VCR** (`tracegraph-connectors` test jar): `CassetteLlmClient` for recording and replaying LLM exchanges deterministically.
- **Provider contract tests**: `LlmClientContractTest` validates 6 behaviors across 5 providers via cassette replay (no live API keys required in CI).
- **Security CI** (`.github/workflows/security.yml`): OWASP dependency-check (fail CVSS ≥ 7), Gitleaks secrets scanning.
- **SBOM generation**: CycloneDX JSON SBOM published as a release artifact via `mvn -Psbom package`.
- **Mutation testing**: Nightly PIT run on `tracegraph-core` via `.github/workflows/mutation.yml`.
- **macOS CI matrix** and JDK 25-ea allow-fail job added to `.github/workflows/ci.yml`.
- **JMH benchmark module** (`tracegraph-bench`): graph dispatch and ReAct loop latency benchmarks; not published to Maven Central.
- **Revapi `api-check` profile**: binary compatibility gate in parent POM; run with `mvn -Papi-check verify`.

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
