# TraceGraph Improvement Plan — Source Audit & Roadmap

*Audit date: 2026-06-10. Grounded in a line-level review of `tracegraph-core` (Executor, exec package),
`tracegraph-observability` (RecordingTraceRecorder, JsonFileTraceStore, JdbcTraceStore),
`tracegraph-connectors` (AnthropicLlmClient, OpenAiLlmClient, ReActAgent), and
`tracegraph-spring-boot-starter` (controllers, ApiKeyAuthFilter, auto-configs).*

Severity: 🔴 fix before next release · 🟠 fix soon · 🟡 plan for · 🔵 strategic

> **Status (2026-06-10):** Fixed in working tree, all tests green: B1, B2, B3 (order + cancel),
> B4 (stale-entry mitigation via `recordEnter` clearing), B6 (guaranteed `recordComplete`),
> B7 (HALTED error step), B8, P3 (`fromStep`/`maxSteps` + `X-Total-Steps`), P5 (streaming I/O),
> P6 (mapper hoist), S1 (startup warning), S3 (constant-time compare), S6 (NUL/blank guard).
> B5 downgraded: `Graph.resume` already returns `Optional` — null is internal to `Executor` only.
> Still open: P1/P2 (append-oriented persistence), P4 (state caps), S2 (redaction/encryption),
> S4 (error sanitization), S5 (taint metadata), §4 outcome enum, §5 context budgets.

---

## 1. Bugs (correctness)

### B1 🔴 Malformed tool-call arguments are silently swallowed
`AnthropicLlmClient.toRequestBody` — when re-serializing an assistant message's tool calls, a JSON
parse failure on `tc.arguments()` is replaced with `Map.of()` (`AnthropicLlmClient.java:118-120`).
The model's actual (possibly malformed) arguments are silently dropped from the conversation
history sent back to the API — the model then sees a tool call with empty input and may
re-invent arguments. This both corrupts the agent loop and *masks hallucinated/malformed output*
that the trace should have captured.
**Fix:** propagate as a structured failure (or pass the raw string through as a text block) and
record the malformed payload in the trace.

### B2 🔴 Consecutive tool results may produce invalid message sequences
Each `Role.TOOL` message becomes its own `{"role":"user"}` message
(`AnthropicLlmClient.java:124-130`). Parallel tool calls (N results) yield N consecutive user
messages instead of one user message with N `tool_result` blocks. Anthropic's API requires all
tool results for a turn in the immediately following user message — N-results-as-N-messages can
be rejected or mis-attributed. **Fix:** coalesce consecutive TOOL messages into one user message.
*(Verify against current API behavior; OpenAiLlmClient should be checked for the analogous case.)*

### B3 🟠 `executeSendAll` violates the documented failure contract and leaks work
`Executor.executeSendAll` (`Executor.java:539-559`):
- `allOf(...).join()` throws the *first future to fail chronologically*, not
  first-by-declaration-order (the documented `parallel(...)` contract).
- On failure, sibling branches are not cancelled — they keep running (and keep spending LLM
  tokens) with their results discarded.
- Branch contexts pass `this.listener`/`this.traceRecorder`, so `ctx.reportUsage` from inside a
  Send branch attributes usage to the *target node name* under the parent executionId while no
  step for that node exists in the trace — orphaned usage that `RecordingTraceRecorder` parks in
  `pendingUsage` and attaches to a *later unrelated step* if a node of the same name runs again.
**Fix:** iterate futures in declaration order for failure selection; cancel remaining futures on
failure; attribute Send-branch usage to the spawning routing node (or introduce per-step tokens).

### B4 🟠 Raw I/O and usage keyed by nodeName collide
`RecordingTraceRecorder.pendingUsage/pendingRawInput/pendingRawOutput` are keyed by `nodeName`
(`RecordingTraceRecorder.java:25-35`). Loops that revisit a node, Send fan-out to one target, or
any concurrency under one executionId overwrite/cross-attribute entries. **Fix:** key by a
per-invocation step token issued at `recordEnter`.

### B5 🟠 `resume()` returns `null` when no checkpoint exists
`Executor.resume` (`Executor.java:178-180`) returns `null` instead of an empty/typed result —
NPE bait for callers and contrary to the project's API-design rules. **Fix:** return
`Optional<ExecutionResult<S>>` or throw a typed exception (pre-1.0 break, document in CHANGELOG).

### B6 🟡 Recorder leaks builders on abnormal termination
`active`, `pendingLineage`, `pendingParent` in `RecordingTraceRecorder` are only drained by
`recordComplete`. An executor `Error`, a killed thread, or a thrown listener leaves the full
in-memory trace builder (states + raw I/O) resident forever. **Fix:** try/finally in
`Executor.run/resume` guaranteeing `recordComplete` (with `FAILED`), plus an eviction policy.

### B7 🟡 `HALTED` (max-steps) and listener-`TERMINATED` runs end without an error step
The trace records `recordComplete(status)` but no step explains *why*; the saved trace shows a
normal-looking prefix with a surprising terminal status. **Fix:** record a synthetic
terminal-reason step or add a `terminationReason` field to `ExecutionTrace`.

### B8 🟡 Dead local `routingTarget` in `loop` (`Executor.java:283`) — harmless, remove.

---

## 2. Performance

### P1 🔴 Trace is memory-only until `recordComplete`, then saved once
`RecordingTraceRecorder` persists only at completion (`recordComplete` → `store.save`). A crash
mid-run loses the entire trace; a long-horizon run (days, thousands of steps, 128K-token raw
outputs) holds everything on heap. This undermines the core product promise exactly where it
matters most. **Fix:** `TraceStore.appendStep(executionId, TraceStep)` (default method = current
load/merge/save for back-compat); flush per-step or per-N-steps; JSONL file format / step table
for JDBC.

### P2 🔴 Whole-trace rewrite per save (O(n²) over a run)
`JsonFileTraceStore.save` rewrites the full file; `JdbcTraceStore` rewrites the full `data_json`
blob; resume re-loads the entire prior trace into the builder. Fixed together with P1.

### P3 🟠 No step pagination on the trace API
`GET /tracegraph/traces/{id}` serializes the whole trace. Add `?fromStep&maxSteps` and a
steps-summary projection (nodeName, status, duration, usage — no state bodies).

### P4 🟠 Unbounded state rendering and duplication
Default `StateRenderer` is `String::valueOf` with no cap; each step stores full `before` and
`after` states with no structural sharing. With conversation-bearing state at frontier context
sizes (1M tokens), every step duplicates the entire history twice. **Fix:** size-capped default
renderer (e.g. 16KB + truncation marker); optional content-addressed state storage
(store-once, reference-thereafter) as a follow-up slice.

### P5 🟡 Serialization buffers entire payload in memory
`mapper.writeValueAsBytes(dto)` + `Files.readAllBytes` — switch to streaming
`writeValue(OutputStream)` / `readValue(InputStream)`.

### P6 🟡 Connector micro-issues
- `new ObjectMapper()` allocated per tool call inside `toRequestBody`
  (`AnthropicLlmClient.java:116`) — hoist to the instance mapper.
- `SimpleContext.logger()` calls `LoggerFactory.getLogger` per invocation — cache.
- No HTTP retry classification: 429/529 with `Retry-After` should be distinguishable from 400s
  so `RetryPolicy`/`RetryingLlmClient` don't re-bill non-retryable failures.
- Request timeouts sized for chat-class models will spuriously fail multi-minute frontier-model
  generations; document defaults and prefer streaming keep-alive for long calls.

---

## 3. Security

### S1 🔴 Replay/resume endpoints are remote execution; auth is opt-in
`POST /tracegraph/traces/{id}/replay` and `/resume` re-execute the graph — with LLM nodes that is
attacker-triggered API spend plus arbitrary node side effects. If no API key is configured the
filter is absent and `/tracegraph/**` is open. **Fix:** mutating endpoints (replay, resume,
delete) deny-by-default unless auth is configured; loud startup warning when web endpoints are
enabled without a key; consider read-only vs. execute key scopes.

### S2 🔴 No redaction or encryption of trace data
`rawInput`/`rawOutput` capture verbatim LLM payloads (gated by `sensitiveDataLogging`, good),
but once enabled they persist in plaintext JSON/JDBC and are served unmasked over REST. State
snapshots are never redacted at all. **Fix:** `TraceRedactor` SPI applied before
`TraceStore.save` (default credential/PII pattern scrubbing, pluggable); optional AES-GCM
encryption for file/JDBC stores; field-level masking option in the REST layer.

### S3 🟠 Non-constant-time API key comparison
`ApiKeyAuthFilter.java:43` uses `String.equals`. Use
`MessageDigest.isEqual(provided.getBytes(UTF_8), expected.getBytes(UTF_8))`.

### S4 🟠 Exception details leak via traces and REST
`ErrorDto` round-trips exception class + message; messages routinely contain connection strings,
paths, hostnames. Add an error-sanitization hook before persistence/serving.

### S5 🟡 Prompt-injection blast radius
Tool results flow back into the model unmarked. Add per-step provenance/taint metadata on
`ToolResult`-derived content so traces can answer "which untrusted input steered this run";
document `interruptBefore` on high-privilege nodes as the HITL control point.

### S6 🟡 Path guard hardening
`JsonFileTraceStore.pathFor` rejects `/ \ ..` — also reject NUL and Windows reserved device
names (`CON`, `PRN`, …). Same review for `FileMemoryStore` scope/key guards.

---

## 4. Hallucination resilience

Where model output can be wrong and what TraceGraph does (or should do) about it:

| Surface | Current behavior | Improvement |
|---|---|---|
| Malformed tool-call JSON | Silently replaced with `{}` (B1) | Fail soft + record raw payload as a first-class trace artifact |
| Unknown `goTo` target from an LLM-driven routing node | Throws `NodeExecutionException` ✓ | Also record the attempted target in the error step for debuggability |
| Refusals / safeguard blocks (frontier models reroute or refuse) | Indistinguishable from normal text; `responseFolder` sees unexpected content | Add a step outcome enum (`OK / REFUSED / SAFEGUARD_REROUTED`) + served-model field on `TraceStep` |
| Looping agents (model never emits stop condition) | `maxSteps` guard → `HALTED` ✓ (but see B7) | Per-execution token/cost budget guard in addition to step count |
| Drift over time (same graph, degrading outputs) | `TraceDiff` exists but matches on exact state equality — noisy states diverge at step 0 | Pluggable step-equivalence (compare selected fields / normalized states); scheduled canary runs + `TraceDiff` vs. baseline = "regression testing for agents" |
| Hallucinated facts in outputs | Out of scope for the runtime | Eval-layer slice: LLM-as-judge scoring attached to traces (builds on existing trace substrate) |

---

## 5. Context-length management

Currently absent — and increasingly the binding constraint with 1M-context models:

- **No token counting or budgeting.** `LlmRequest` carries `maxTokens` (output) only. Nothing
  tracks cumulative prompt growth as conversation-bearing state accretes across a loop.
- **No trimming/summarization utilities.** `ChatNode`/`ReActAgent` send whatever the
  `requestBuilder` constructs; a long ReAct run grows the message list unboundedly until the API
  rejects it (`LlmHttpException` 400) — which then looks like a node failure, not a
  context-budget failure.
- **Plan:**
  1. `ContextBudget` policy on `ChatNode`/`ReActAgent` (max prompt tokens, estimated via
     chars/4 heuristic now, provider token-count endpoints later).
  2. Pluggable `MessageWindowStrategy` — keep-system + sliding window, or summarize-evicted via
     a cheap model. Both are pure functions over `List<ChatMessage>`, testable, no new deps.
  3. Surface prompt-size-per-step in traces (already have `Usage.promptTokens`) and flag
     near-limit steps in `CostReport` — early-warning signal before runs start failing.
  4. Trace-side: P4's state caps are the storage mirror of this problem; ship together.

---

## 6. Prioritized roadmap

**Milestone 1 — correctness & safety (release blocker)**
1. B1 tool-arg swallowing, B2 tool-result coalescing (small, high impact)
2. S1 deny-by-default on mutating endpoints + startup warning
3. S3 constant-time compare, B5 `resume` null return, B8 dead code

**Milestone 2 — durable long-horizon traces**
4. P1+P2 `appendStep` incremental persistence (JSONL + step table)
5. B4 step-token keying, B6 recorder leak fix, B7 termination-reason
6. P3 step pagination, P4 capped renderer

**Milestone 3 — enterprise trust**
7. S2 `TraceRedactor` SPI + encryption-at-rest option, S4 error sanitization

**Milestone 4 — frontier-model readiness**
8. Context-budget + window strategies (§5)
9. Step outcome enum + served-model capture; dollar-denominated `CostReport`
10. B3 SendAll contract fix + branch usage attribution
11. Recorded-LLM replay stubbing (`RecordedLlmClient` + `ReplayRunner` stub mode) — converts
    replay from "re-run and hope" into deterministic debugging; the headline differentiator

**Milestone 5 — ecosystem**
12. Native Anthropic SSE streaming; OTel GenAI semantic conventions in `OtelNodeListener`;
    Spring AI `ChatModel → LlmClient` adapter; MCP client connector

---

## 7. Open questions / to verify

- Anthropic API behavior on consecutive same-role messages (B2) — test against the live API.
- Whether `TraceWebAutoConfiguration` registers `ApiKeyAuthFilter` unconditionally or only when
  a key property is set (S1 assumes opt-in; confirm in auto-config source).
- `OpenAiLlmClient` for B1/B2 analogues (tool-arg handling, message sequencing).
- Frontier-model API surface (effort/thinking params, served-model response field) before
  building the outcome enum.
