# TraceGraph Whole-Library Improvement Plan

*2026-06-10. Follows the module-level audit in `improvement-plan.md` (core/observability/
connectors/starter — those findings are not repeated here). This plan covers the full
14-module reactor including the modules that grew past the documented architecture:
RAG, A2A, UI, Eval, plus guardrails / MCP / multi-agent / structured-output surfaces.*

Severity: 🔴 next release · 🟠 soon · 🟡 planned · 🔵 strategic

---

## 0. Cross-cutting findings

### X1 🔴 Documentation debt: CLAUDE.md / README describe maybe half the library
CLAUDE.md documents 6 modules; the build has 14 (core, runtime, connectors, memory,
observability, rag, a2a, ui, spring-boot-starter, eval, bench, e2e, demo, bom). Undocumented
surfaces include: `VectorStore`/`EmbeddingClient` SPIs, guardrails, `McpClient`, multi-agent
nodes (`SupervisorAgent`, `GroupChatAgent`, `VotingNode`, `HandoffNode`), `StructuredOutputNode`,
`SemanticLlmCache`, `PromptLibrary`, `TtlMemoryStore`/`SummaryMemory`/`BufferWindowMemory`,
the whole eval module, `TokenBudgetListener`/`CostBudgetListener`, and seven+ auto-configs.
An AI-assisted repo whose AI context file is stale compounds errors on every future change.
**Action:** regenerate CLAUDE.md module table + conventions from source; add per-module
`package-info.java` consistency; document which modules are stable vs. incubating.

### X2 🔴 Duplicate implementations across modules
Verified duplicates: `InMemoryVectorStore`, `MockEmbeddingClient`, `OpenAiEmbeddingClient`
exist in BOTH `tracegraph-connectors` (`connectors.vector` / `connectors.llm`) and
`tracegraph-rag` (`io.tracegraph.rag`). Two copies will drift (and likely already have).
**Action:** pick one home (RAG depends on connectors, or both depend on a shared SPI in
core — `VectorStore` SPI is already in core), deprecate the other copy for one minor, delete.

### X3 🟠 Exception-type proliferation for the same failure shape
`LlmHttpException`, `EmbeddingHttpException`, `VectorStoreHttpException`, `A2AHttpException`,
`McpException`, `PgVectorException` — six ways to say "remote call failed (status, body)".
Retry classification (429/5xx vs 4xx) must be re-implemented per type; `RetryingLlmClient`
covers only one. **Action:** a common `RemoteCallException` base (or interface with
`statusCode()`/`isRetryable()`) in connectors; subtypes keep their names for compat.

### X4 🟠 HTTP adapter boilerplate ×N
Anthropic/OpenAI/Gemini/DeepSeek/Ollama LLM clients + 4 embedding clients + 4 remote vector
stores each hand-roll JDK HttpClient + Jackson + error mapping. Bugs like B1/B2 (audit batch 1)
get fixed in one adapter and persist in the others. **Action:** extract a package-private
`JsonHttpSupport` (request build, send, status check, parse, timeout) used by all adapters;
then sweep every adapter for the B1/B2 classes of bug (silent fallback on malformed payloads;
message-sequencing contracts).

### X5 🟠 Secrets handling is inconsistent across adapters
Each client takes `apiKey` as a constructor string; headers differ; some may log request
bodies via `sensitiveDataLogging`. The new `Redactor` covers traces — but guardrails has
`RegexPiiGuardrail` which overlaps it. **Action:** one redaction primitive shared by
guardrail + trace layers; audit every adapter's toString/log paths for key leakage.

### X6 🟡 Thread-safety / virtual-thread review for the new modules
Concurrency rules were written for core. `InMemoryAgentBus`, `SemanticLlmCache`,
`PromptLibrary`, `TtlMemoryStore` need the same review: no `synchronized` over I/O,
`ConcurrentHashMap` semantics, TTL sweeping strategy (scheduled vs on-access).

### X7 🟡 Versioning and API-stability tiers
`0.4.0-SNAPSHOT` with 14 modules and a 70+ type public surface. Before 0.5: declare
stability tiers (core/runtime/observability = stable; rag/a2a/eval = incubating) in the
docs and enforce with japicmp or Revapi in CI for the stable tier.

---

## 1. Per-module findings

### tracegraph-rag 🟠
- 4 remote vector stores (PgVector, Pinecone, Qdrant, Weaviate) — verify: upsert batching,
  pagination on query, metadata filtering parity, and integration-test strategy (currently
  unit-only? remote stores are untestable without contract tests — add a `VectorStoreContractTest`
  abstract class all impls extend, like `LlmClientContractTest` does for LLMs).
- `RecursiveCharacterSplitter`: check token-vs-char splitting (char-based splitters overflow
  token budgets on CJK/code); expose overlap validation.
- `Bm25Scorer`/`HybridRetriever`: score normalization between BM25 and cosine spaces is the
  classic correctness trap — verify and document the fusion method (RRF vs weighted).
- `RagPipeline` should report retrieval steps through `TraceRecorder` (retrieved chunk IDs +
  scores per step) — RAG observability is a differentiator no one in the JVM space has.

### tracegraph-a2a 🟠
- `A2AHttpClient`/`A2AController`: confirm the controller is under the `/tracegraph` auth
  filter or has its own — agent-to-agent ingress without auth = remote graph invocation.
- `AgentMessage` should carry `correlationId`/`executionId` so cross-agent traces can be
  stitched (the fork/parent lineage pattern already exists — reuse it for bus hops).
- `InMemoryAgentBus`: delivery semantics (at-most-once?), timeout behavior, backpressure.

### tracegraph-ui 🟡
- `UiIndexController`/`GraphRenderController`: served HTML/JS — XSS review (trace content is
  attacker-influenced via prompts; rendering raw state strings into HTML is the risk),
  CSP headers, and the same auth filter coverage.
- Step pagination (new `fromStep`/`maxSteps`) should be adopted by the UI fetch layer.

### tracegraph-eval 🔵 (strategic asset)
- Already has metrics (BLEU/ROUGE/F1/embedding-sim/LLM-judge), golden traces, baselines, and
  fault-localization experiments — this is the "regression testing for agents" story.
  Gaps: `LlmJudgeMetric` cost/budget controls and caching; CI-friendly `EvalSuite` runner
  (JUnit 5 engine or Maven plugin) so users gate PRs on eval baselines; baseline drift
  alerting tied to `TraceDiff`.
- `faultloc` experiments look research-grade (paper work) — decide: productize or move to
  a `research`-scoped module so the public API doesn't accrete experiment types.

### tracegraph-connectors 🟠
- `ToolMethodAdapter` (reflection-based tools): schema generation correctness for nested/
  generic params; exception mapping into `ToolResult`.
- `StructuredOutputNode`: retry-on-invalid-JSON loop bounds and trace visibility of failed
  parses (same B1 lesson: never swallow the malformed payload).
- Multi-agent nodes (`SupervisorAgent`, `GroupChatAgent`, `VotingNode`): per-sub-agent usage
  attribution (same hole as Send fan-out, B3/B4) and trace step shape for group turns.
- `SemanticLlmCache`: similarity-threshold false-hit risk — cache hits must be marked in the
  trace (a cached answer that's subtly wrong is a debugging nightmare without that marker).
- Apply audit B1/B2 sweep to Gemini/DeepSeek/Ollama clients (X4).

### tracegraph-memory 🟡
- `TtlMemoryStore` expiry strategy under virtual threads; `SummaryMemory` summarization cost
  controls; `BufferWindowMemory` aligns with the context-budget plan (§5 of module audit) —
  unify into one documented "context management" story instead of three ad-hoc utilities.

### tracegraph-runtime 🟡
- `ContextPropagatingExecutor`: verify MDC/context propagation works under virtual threads
  and nested parallel/SendAll; document interaction with user-supplied executors.

### tracegraph-spring-boot-starter 🟠
- Seven+ auto-configs now (LLM, Memory, MCP, RAG, Embedding, VectorStore, A2A, CORS, MDC) —
  needs a single configuration-metadata pass (`spring-configuration-metadata.json` complete?),
  a properties reference doc, and condition-order tests (the Memory `before` ordering trick
  must hold for the newer ones too).
- `CorsAutoConfiguration`: default origin policy review — permissive CORS + API-key auth in
  headers is a credential-exposure combo.

### tracegraph-bench / e2e / demo 🟡
- Bench: commit or gitignore JMH generated sources (today's `-Werror` failure trap);
  pin `spring-boot-maven-plugin` version in demo (build warning).
- e2e: extend to cover replay + redaction + flush paths (currently exercises happy path).

---

## 2. Sequenced roadmap

| # | Slice | Modules | Size |
|---|---|---|---|
| 1 | X1 docs regeneration (CLAUDE.md + module table) | repo | S |
| 2 | X2 de-duplicate vector/embedding classes | rag, connectors | S |
| 3 | A2A + UI auth coverage check & fix | a2a, ui, starter | S |
| 4 | X3+X4 shared HTTP support + B1/B2 sweep of all adapters | connectors, rag, a2a | M |
| 5 | P2 append-oriented trace persistence (carried from module audit) | observability | M |
| 6 | RAG trace integration (chunk IDs/scores as step data) | rag, observability | M |
| 7 | Eval-as-CI (JUnit engine / Maven plugin + baseline gates) | eval | M |
| 8 | SemanticLlmCache hit-marking in traces | connectors, observability | S |
| 9 | Context-management unification (BufferWindow/Summary/TokenBudget + budgets) | memory, connectors | M |
| 10 | X7 API tiers + japicmp gate | build/CI | S |
| 11 | Encryption-at-rest for file/JDBC stores | observability, memory | M |
| 12 | Step outcome enum + served-model capture (frontier-model readiness) | core, observability, connectors | M |

Items 1–3 are low-risk and high-leverage; I'd land them as one PR. Item 4 is the highest
defect-density bet (the audit found 2 real bugs in the one adapter inspected; nine more
adapters share the pattern).

---

## 3. Open questions

- Should `tracegraph-rag` depend on `tracegraph-connectors` (resolves X2 naturally) or
  should embedding/vector SPIs move wholly into core SPI + impls split per backend module?
- Is `eval.faultloc` intended as public API or research scaffolding for the paper in
  `research/`? Affects item 7's surface.
- UI module: is it a throwaway demo or the start of the visualization product? Determines
  whether XSS/CSP work (item 3) is a patch or an architecture investment.
