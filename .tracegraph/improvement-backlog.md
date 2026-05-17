# TraceGraph Auto-Improvement Backlog

<!--
This file is consumed by the scheduled auto-improvement agent (cron 0 */5 * * *).
See .tracegraph/README.md for the contract.
- Top unchecked item is picked each tick.
- Mark `- [x]` only after `mvn -B test` passes on the auto branch.
- When fully checked, the agent appends 5–10 new items under the next research-angle.
-->

research-angle: developer-experience

## Active backlog

- [x] **Human-in-the-loop state-edit endpoint** (LangGraph Studio parity) — extend `TraceReplayController` with `POST /tracegraph/traces/{id}/resume` body to override state at an `interruptPending` checkpoint before resuming. Address weakness: HITL today is resume-only, no state mutation.
- [x] **Time-travel fork endpoint** (LangGraph time-travel) — `POST /tracegraph/traces/{id}/fork?step=N` returning new executionId; wraps existing `ReplayRunner.reRunFrom`. Weakness: replay is library-only, no REST surface.
- [x] **Structured output node** (Spring AI `BeanOutputConverter`, BAML) — `StructuredOutputNode<S, T>` in `tracegraph-connectors` that wraps an `LlmClient`, injects a JSON schema into the prompt, parses + validates response into `T` via Jackson, retries on parse failure (configurable, default 1). Tests with `MockLlmClient`.
- [x] **Vector-store SPI** (LangChain4j `EmbeddingStore`, Spring AI `VectorStore`) — `VectorStore` SPI in `tracegraph-core/spi` (`add`, `similaritySearch(queryEmbedding, k)`), `InMemoryVectorStore` (cosine, brute-force) in `tracegraph-connectors`. Wire via `Graph.Builder.vectorStore(...)` exposed on `Context`.
- [x] **Embeddings client SPI** (parallel to `LlmClient`) — `EmbeddingClient.embed(List<String>) → List<float[]>` in `tracegraph-connectors`, `MockEmbeddingClient` for tests, `OpenAiEmbeddingClient` adapter using the existing `HttpClient` pattern.
- [x] **Streaming tool-call deltas** (OpenAI/Anthropic streaming tool use) — extend `LlmStreamChunk` with optional `toolCallDelta(index, nameDelta, argsDelta)`; update `OpenAiLlmClient.stream` to surface them. Weakness: streaming is text-only today.
- [x] **Token-budget guard listener** (Langfuse cost guards) — `TokenBudgetListener(maxPromptTokens, maxCompletionTokens)` in `tracegraph-observability` that throws `BudgetExceededException` from `onUsage` once the per-execution sum exceeds threshold. Tests via `LlmCostListener` companion.
- [x] **Eval-harness module sketch** (DeepEval, Ragas, LangSmith evals) — new `tracegraph-eval` module: `GoldenTrace` loader from `JsonFileTraceStore`, `EvalRunner` re-executes a `Graph<S>` against each golden seed, `EvalReport` aggregating pass/fail + state diff. No external eval LLM dependency in v1.
- [x] **Supervisor multi-agent pattern** (CrewAI, AutoGen) — `SupervisorAgent<S>` factory in `tracegraph-connectors` producing a `Graph<S>` where a router LLM picks among N named `ReActAgent`s; uses `RoutingNode` + `subgraph` building blocks. Test with two trivial agents and a `MockLlmClient` routing harness.
- [x] **OTLP trace exporter** (Langfuse, Arize Phoenix) — `OtlpTraceExporter` in `tracegraph-observability` that consumes a `TraceStore` and emits per-step OTel spans (beyond `OtelNodeListener`'s live spans) for offline re-export. Weakness: persisted traces aren't replayable into APM tools.
- [ ] **Docs gap audit** (LangGraph "Concepts" tree) — produce `docs/site/docs/competitive-gap-audit.md` listing missing concept pages (e.g., persistence, time-travel, breakpoints, double-texting, multi-agent), each linking to the existing TraceGraph primitive or marking `TODO: needs page`. No code change.
- [x] **Comparative micro-bench** — extend `tracegraph-bench` with a JMH benchmark contrasting `Graph.run` vs a hand-written sync executor on a linear 10-node graph. Document overhead in `tracegraph-bench/README.md`.
