# TraceGraph Improvement Roadmap v2 — Engineering Programs

*2026-06-10. Third planning layer: `improvement-plan.md` (module-level bugs/security, mostly
fixed in PR #127) and `library-improvement-plan.md` (whole-library findings X1–X7) stay the
tactical backlog. This document adds what those don't cover: newly confirmed findings,
and the standing engineering programs (quality, performance, release, product, DX) that
turn one-off audits into a repeatable practice.*

---

## 1. Newly confirmed findings (from open-question follow-up)

### N1 🔴 `A2AController` ingress is unauthenticated
`POST /a2a/messages` lives **outside** the `/tracegraph/*` pattern that `ApiKeyAuthFilter`
registers, so agent-to-agent message ingress is open even when an API key is configured.
Anyone who can reach the port can inject messages into the agent bus.
**Fix options (pick one):** move the route under `/tracegraph/a2a/messages` (breaking for
existing peers — pre-1.0 acceptable, changelog it); or register the filter for both patterns;
or give A2A its own key property (peers are machines — separate credentials from the human
trace-UI key is arguably correct). Recommendation: separate `tracegraph.a2a.api-key`,
deny-by-default when unset.

### N2 ✅ resolved-by-inspection
UI controllers (`/tracegraph/ui`) are inside the auth filter pattern. CORS defaults are
localhost-only (`5173`, `3000`) — acceptable; document that widening origins while using
header-based API keys requires care.

---

## 2. Quality engineering program

The audits found bugs by reading; these make finding them mechanical.

- **Q1 Contract-test families.** `LlmClientContractTest` exists for LLM clients — replicate
  the pattern: `VectorStoreContractTest`, `EmbeddingClientContractTest`,
  `MemoryStoreContractTest`, `CheckpointStoreContractTest`, `TraceStoreContractTest`.
  Every new backend implements the abstract test class; behavioral drift between impls
  (the X2 duplication problem) becomes a compile-time obligation.
- **Q2 Adapter conformance fixtures.** For each HTTP adapter, recorded request/response
  fixtures (golden JSON) asserting exact wire format — the B1/B2 bug class (malformed-payload
  fallbacks, message-sequencing) is only catchable this way without live APIs. One fixture
  suite per provider; run in CI; refresh procedure documented.
- **Q3 Fuzzing the parsers.** SSE chunk assembly, tool-call JSON parsing,
  `StructuredOutputNode`, trace deserialization — feed with Jazzer (OSS-Fuzz-style) or, at
  minimum, property-based tests (jqwik) with adversarial strings (huge, emoji, control chars,
  truncated JSON). These parse *model output* — i.e., untrusted input.
- **Q4 Mutation testing budget.** `mutation.yml` workflow exists — set thresholds per stable
  module (core ≥ 75% killed) and make it advisory-to-blocking over two releases.
- **Q5 Concurrency tests.** jcstress (or stress-loop tests) for `InMemoryAgentBus`,
  `SemanticLlmCache`, `RecordingTraceRecorder` under parallel/SendAll, `TtlMemoryStore`
  expiry races. Virtual-thread pinning detector (`-Djdk.tracePinnedThreads`) in one CI job.

## 3. Performance program

- **PF1 Overhead budget.** `TraceOverheadBenchmark` exists; define the budget it enforces:
  tracing overhead ≤ N µs/node and ≤ X% of a 1 ms node (pick from current numbers), fail CI
  on >20% regression vs. a stored JMH baseline.
- **PF2 Benchmark the new hot paths.** Redaction (regex over 100KB raw I/O), incremental
  flush (per-N-steps serialization cost vs. flush interval), `SemanticLlmCache` lookup,
  BM25/hybrid retrieval, step pagination on a 10k-step trace.
- **PF3 Memory profile of long runs.** A soak test: 50k-step synthetic run with 64KB states;
  assert bounded heap with flush enabled. This is the P1/P2 acceptance test.
- **PF4 Startup cost of the starter.** 9 auto-configs — measure condition-evaluation cost,
  ensure all are no-op without their trigger classes (Boot's startup report in one CI job).

## 4. Release engineering

- **R1 Stability tiers + japicmp.** Tier modules (stable: core, runtime, observability,
  memory, starter; incubating: rag, a2a, ui, eval). japicmp/Revapi gate on stable tier;
  incubating gets `@ApiStatus.Experimental`-style annotation or package-info notice.
- **R2 1.0 criteria (write them down now).** Suggested: P2 landed (append persistence),
  N1 + encryption-at-rest closed, contract tests for all SPI impls, two consecutive minor
  releases without a breaking change on stable tier, docs site complete.
- **R3 Reproducible builds + SBOM.** CycloneDX plugin + provenance in release workflow —
  cheap, and the enterprise positioning demands it.
- **R4 Bench hygiene.** Gitignore JMH generated sources (today's `-Werror` trap); pin
  `spring-boot-maven-plugin` in demo; fix the dependabot PRs that just landed.

## 5. Product / differentiation roadmap (sequenced)

1. **Recorded-LLM replay stubbing** — the single highest-moat feature; all substrate exists
   (`rawInput`/`rawOutput` capture, `ReplayRunner`, idempotency keys). Replay becomes
   deterministic debugging instead of re-billed re-execution.
2. **RAG trace integration** — retrieved chunk IDs + scores as step data; "why did the
   agent answer this" becomes inspectable. No JVM competitor has it.
3. **Eval-as-CI** — JUnit 5 engine or Maven plugin wrapping `EvalSuite` + baselines;
   `TraceDiff` drift gates. Positions eval module as "regression testing for agents."
4. **Cache/outcome visibility** — `SemanticLlmCache` hit markers; step outcome enum
   (`OK/REFUSED/SAFEGUARD_REROUTED`) + served-model capture (frontier-model reroutes).
5. **OTel GenAI semconv + Spring AI adapter** — distribution: traces land in existing
   dashboards; Spring AI users adopt without rewriting model wiring.
6. **MCP server for traces** — expose TraceStore over MCP so Claude Code / IDE agents can
   query and diff traces conversationally. Cheap demo, large mindshare value.
7. **UI decision** — promote to product (then: CSP, XSS-hardening, step-paginated fetch,
   diff view) or freeze as demo. Don't let it drift in between.

## 6. Developer experience

- **D1 CLAUDE.md regeneration** (X1) plus `docs/adr/` for the decisions these plans keep
  re-litigating (single-`<S>` generics, branch invisibility, lossy throwables).
- **D2 Quickstarts per persona:** "trace an existing agent in 5 min" (starter),
  "build a ReAct agent" (connectors), "replay & diff a failure" (observability),
  "gate CI on evals" (eval). Each as a runnable example module.
- **D3 Spring configuration metadata audit** — every `tracegraph.*` property present with
  description + default so IDE completion works; generate a properties reference page.
- **D4 Javadoc gate** on stable-tier public API (the `generate-javadoc` flow exists).

## 7. Suggested execution order (next 4 PRs)

| PR | Content | Why first |
|---|---|---|
| 1 | N1 A2A auth + R4 bench hygiene + dependabot merges | security hole + repo hygiene, all small |
| 2 | X2 de-dup + Q1 contract-test families | stops drift before adapter sweep |
| 3 | X4 shared HTTP support + B1/B2 sweep + Q2 fixtures | highest expected defect yield |
| 4 | Product #1: recorded-LLM replay stubbing | the moat |
