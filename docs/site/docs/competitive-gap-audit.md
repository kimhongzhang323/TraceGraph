# Competitive Gap Audit

This page maps concept coverage across peer JVM/agent frameworks
([LangGraph](https://langchain-ai.github.io/langgraph/concepts/),
[LangChain4j](https://docs.langchain4j.dev/),
[Spring AI](https://docs.spring.io/spring-ai/reference/),
[Semantic Kernel](https://learn.microsoft.com/en-us/semantic-kernel/),
[CrewAI](https://docs.crewai.com/),
[AutoGen](https://microsoft.github.io/autogen/))
to existing TraceGraph documentation.

For each concept, the **Status** column is one of:

| Status | Meaning |
|---|---|
| ✅ Documented | A dedicated page exists and covers the concept end-to-end. |
| 🔶 Partial | The concept is touched in a tutorial or reference page but lacks a dedicated concept page. |
| ❌ TODO: needs page | The primitive exists in code (see CLAUDE.md) but no concept page describes it yet. |
| 🚫 Not applicable | The concept is deliberately out of scope for TraceGraph. |

---

## 1. Persistence

**Peer coverage:** LangGraph "Persistence" concept page, LangChain4j `ChatMemory`, Spring AI `VectorStore`.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Checkpoint store SPI | `CheckpointStore`, `InMemoryCheckpointStore`, `JdbcCheckpointStore` | 🔶 Partial — covered in [Memory & Checkpointing](concepts/memory.md); no dedicated persistence concept page. |
| Checkpoint lifecycle (write-after-node, before-edge-resolution) | Graph executor | ❌ TODO: needs page |
| Thread / execution isolation | `executionId` per `graph.run()` | ❌ TODO: needs page |
| Cross-execution shared memory | `MemoryStore` SPI + `InMemoryMemoryStore`, `FileMemoryStore`, `JdbcMemoryStore` | 🔶 Partial — mentioned in [Memory & Checkpointing](concepts/memory.md); no page on cross-execution KV store. |
| At-least-once node semantics on resume | Graph executor | ❌ TODO: needs page |

**Recommended page:** `concepts/persistence.md` — cover `CheckpointStore` lifecycle, `MemoryStore` scopes, and when to use each.

---

## 2. Time-Travel / Replay

**Peer coverage:** LangGraph "Time Travel" concept page; no equivalent in LangChain4j/Spring AI/Semantic Kernel.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Trace recording | `TraceRecorder`, `RecordingTraceRecorder`, `TraceStore` | 🔶 Partial — [Tutorial 10](tutorial/10-replay-and-diff.md) covers the API but there is no concept page. |
| Replay from any step | `ReplayRunner.reRunFrom(stepIndex)` | 🔶 Partial — [Tutorial 10](tutorial/10-replay-and-diff.md) only. |
| Fork lineage tracking | `forkedFromExecutionId`, `forkedFromStepIndex` | 🔶 Partial — [Tutorial 10](tutorial/10-replay-and-diff.md) only. |
| Seed override on replay | `ReplayRunner.reRunFrom(index, seedOverride)` | 🔶 Partial — [Tutorial 10](tutorial/10-replay-and-diff.md) only. |
| Trace diffing | `TraceDiff.between(left, right)` | 🔶 Partial — [Tutorial 10](tutorial/10-replay-and-diff.md) only. |
| Persistent traces | `JsonFileTraceStore`, `JdbcTraceStore` | 🔶 Partial — [Tutorial 10](tutorial/10-replay-and-diff.md) only. |

**Recommended page:** `concepts/time-travel.md` — explain the `TraceRecorder` / `TraceStore` / `ReplayRunner` / `TraceDiff` pipeline as a first-class concept, separate from the step-by-step tutorial.

---

## 3. Breakpoints / Human-in-the-Loop

**Peer coverage:** LangGraph "Breakpoints" and "Human-in-the-loop" concept pages; CrewAI "Human Input on Execution".

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Interrupt before/after a node | `Builder.interruptBefore(name...)`, `Builder.interruptAfter(name...)` | 🔶 Partial — [Tutorial 11](tutorial/11-hitl-interrupts.md) covers the API; no concept page. |
| INTERRUPTED status and checkpoint write | `Status.INTERRUPTED` + `CheckpointStore` | 🔶 Partial — [Tutorial 11](tutorial/11-hitl-interrupts.md) only. |
| State mutation before resume | `CheckpointStore.save(cp.withState(...))` | 🔶 Partial — [Tutorial 11](tutorial/11-hitl-interrupts.md) only. |
| REST resume endpoint | `POST /tracegraph/traces/{id}/resume` | 🔶 Partial — [Tutorial 11](tutorial/11-hitl-interrupts.md) + [REST API reference](reference/rest-api.md). |
| State-override resume (body with new state) | `TraceReplayController` | ❌ TODO: needs page |

**Recommended page:** `concepts/breakpoints.md` — cover interrupt modes, the checkpoint-then-pause flow, state inspection, mutation, and REST resume; link to the HITL cookbook.

---

## 4. Double-Texting

**Peer coverage:** LangGraph "Double-Texting" concept page (handles concurrent `run` calls on the same thread/execution via queue, reject, or rollback strategies).

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Concurrent runs against same execution ID | Not implemented — `graph.run()` creates a new `executionId` per call | 🚫 Not applicable — TraceGraph uses per-run IDs; re-entrant runs against the same thread/ID are not a design goal in v0.x. |
| Run queuing / reject policy | Not implemented | ❌ TODO: needs page — should document the intentional design choice and recommended workaround (immutable `executionId`s). |

**Recommended page:** `concepts/double-texting.md` — short page explaining the LangGraph concept, why TraceGraph's `executionId`-per-run model sidesteps most double-texting problems, and how to implement request deduplication in the application layer.

---

## 5. Multi-Agent Patterns

**Peer coverage:** LangGraph "Multi-agent", "Supervisor", "Swarm" concept pages; CrewAI "Crews & Agents"; AutoGen "Agent Teams".

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Subgraphs | `Builder.subgraph(name, Graph<S> inner)` | ❌ TODO: needs page — no concept page on embedding a graph as a node. |
| Supervisor pattern | `SupervisorAgent<S>` factory in `tracegraph-connectors` | ❌ TODO: needs page |
| ReAct agent | `ReActAgent<S>` factory | 🔶 Partial — [Tutorial 08](tutorial/08-react-agent.md) + [Cookbook](cookbook/react-agent.md); no concept page on agent loop design. |
| Dynamic routing (handoff) | `RoutingNode<S>`, `NodeResult.goTo(name, state)` | 🔶 Partial — touched in [Edges & Routing](concepts/edges-routing.md); `RoutingNode` is not explicitly mentioned. |
| Agent handoff via Send | `NodeResult.sendAll(...)` | 🔶 Partial — [Tutorial 05](tutorial/05-parallel-and-send.md) covers `sendAll`; no concept page on using it for agent fan-out. |

**Recommended page:** `concepts/multi-agent.md` — cover subgraph embedding, supervisor vs. swarm topologies, and how `RoutingNode` + `sendAll` implement dynamic handoff in TraceGraph.

---

## 6. Streaming

**Peer coverage:** LangGraph "Streaming" concept page; Spring AI `StreamingChatModel`; LangChain4j streaming.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Graph event streaming | `Graph.stream(initial)` → `Flow.Publisher<NodeEvent<S>>` | ❌ TODO: needs page |
| Event types | `NodeEnter`, `NodeExit`, `NodeRetry`, `Failed`, `Complete` | ❌ TODO: needs page |
| Back-pressure | `SubmissionPublisher` with configurable buffer (default 256); producer blocks when full | ❌ TODO: needs page |
| Spring Boot SSE endpoint | `POST /tracegraph/traces/stream` via `TraceStreamController` | ❌ TODO: needs page |
| LLM streaming with tool-call deltas | `LlmClient.stream(...)` → `Flow.Publisher<LlmStreamChunk>`; `toolCallDelta` | ❌ TODO: needs page |

**Recommended page:** `concepts/streaming.md` — cover the `Flow.Publisher` model, event types, back-pressure contract, Spring Boot SSE integration, and LLM streaming.

---

## 7. Map-Reduce / Dynamic Fan-Out

**Peer coverage:** LangGraph "Map-Reduce" concept page (uses the `Send` API).

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Static parallel branches | `parallel(name, branches, merger)` | 🔶 Partial — [Tutorial 05](tutorial/05-parallel-and-send.md) covers the API; no concept page. |
| Dynamic fan-out (`Send` API) | `NodeResult.sendAll(List<Send<S>>, Merger<S>, currentState)` | 🔶 Partial — [Tutorial 05](tutorial/05-parallel-and-send.md) covers the API; no concept page. |
| Merge semantics | User-supplied `Merger<S>` (results merged in declaration order for static; by arrival order for `sendAll`) | ❌ TODO: needs page |
| Failure propagation | First-by-declaration-order failure wins | ❌ TODO: needs page |

**Recommended page:** `concepts/map-reduce.md` — dedicated concept page explaining both static `parallel` and dynamic `sendAll`, merge semantics, and failure handling.

---

## 8. Dynamic Routing / Commands

**Peer coverage:** LangGraph `Command` type (combines state update + routing target in a single return value).

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Conditional edges | Predicate-based `Edge<S>` | ✅ Documented — [Edges & Routing](concepts/edges-routing.md). |
| Routing node (`goTo` override) | `RoutingNode<S>`, `NodeResult.goTo(name, state)` | ❌ TODO: needs page — `RoutingNode` is the TraceGraph equivalent of LangGraph's `Command`; needs dedicated coverage. |
| Unknown target handling | `NodeExecutionException` thrown for unresolved `goTo` | ❌ TODO: needs page |

**Recommended page:** expand `concepts/edges-routing.md` with a `RoutingNode` section, or add `concepts/routing-node.md`.

---

## 9. Eval Harness

**Peer coverage:** LangSmith evals; DeepEval; Ragas; AutoGen's `AgentEval`.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Golden trace loading | `GoldenTrace` loader from `JsonFileTraceStore` in `tracegraph-eval` | ❌ TODO: needs page |
| Re-execution against golden seeds | `EvalRunner` in `tracegraph-eval` | ❌ TODO: needs page |
| Pass/fail aggregation | `EvalReport` in `tracegraph-eval` | ❌ TODO: needs page |

**Recommended page:** `concepts/eval-harness.md` — introduce the `tracegraph-eval` module, `GoldenTrace`, `EvalRunner`, and `EvalReport`; link to the tutorial.

---

## 10. Visualization

**Peer coverage:** LangGraph Studio graph visualizer; Semantic Kernel graph export.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Mermaid export | `Graph.toMermaid()` | ❌ TODO: needs page |
| PlantUML export | `Graph.toPlantUml()` | ❌ TODO: needs page |
| Subgraph clusters in render | `subgraph` / `package` cluster in output | ❌ TODO: needs page |

**Recommended page:** `concepts/visualization.md` — show how to export and render a graph definition diagram.

---

## 11. Token / Cost Tracking

**Peer coverage:** Langfuse cost tracking; LangSmith token usage views.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Per-node usage reporting | `NodeListener.onUsage(nodeName, promptTokens, completionTokens)` | ❌ TODO: needs page |
| Cumulative cost listener | `LlmCostListener` in `tracegraph-observability` | ❌ TODO: needs page |
| OTel token attributes | `llm.usage.input_tokens`, `llm.usage.output_tokens`, `llm.usage.total_tokens` span attributes | 🔶 Partial — [Observability](concepts/observability.md) covers OTel broadly; token attributes not called out. |
| Per-step usage in trace | `TraceStep.Usage(promptTokens, completionTokens)` | ❌ TODO: needs page |
| Budget guard | `TokenBudgetListener(maxPromptTokens, maxCompletionTokens)` in `tracegraph-observability` | ❌ TODO: needs page |

**Recommended page:** `concepts/cost-tracking.md` — cover `onUsage`, `LlmCostListener`, `TokenBudgetListener`, and OTel token attributes.

---

## 12. Structured Output

**Peer coverage:** Spring AI `BeanOutputConverter`; BAML (structured output DSL); LangChain4j `AiServices`.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Schema injection + parse | `StructuredOutputNode<S, T>` in `tracegraph-connectors` | ❌ TODO: needs page |
| Retry on parse failure | Configurable, default 1 retry | ❌ TODO: needs page |

**Recommended page:** add a `StructuredOutputNode` section to [Connectors](concepts/connectors.md), or create `concepts/structured-output.md`.

---

## 13. Retry Policy

**Peer coverage:** Resilience4j; Spring Retry; LangChain4j auto-retry.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Per-node retry policy | `.node(name, fn, policy)` | 🔶 Partial — [Tutorial 03](tutorial/03-retries-and-failures.md) covers the API; no concept page. |
| Graph-level default policy | `.defaultRetryPolicy(policy)` | 🔶 Partial — [Tutorial 03](tutorial/03-retries-and-failures.md) only. |
| Backoff strategy | `RetryPolicy` (executor handles backoff) | ❌ TODO: needs page — no concept page explains the backoff contract or how `Error` / `InterruptedException` bypass retries. |
| Idempotency key | `ctx.idempotencyKey()` | ❌ TODO: needs page |
| Retry listener event | `NodeListener.onRetry` | ❌ TODO: needs page |

**Recommended page:** `concepts/retry-policy.md` — cover `RetryPolicy`, per-node vs. graph-level defaults, backoff, `Error`/`InterruptedException` short-circuit, and idempotency keys.

---

## 14. Spring Boot Integration

**Peer coverage:** Spring AI auto-configuration; LangChain4j Spring Boot starter.

| Sub-concept | TraceGraph primitive | Status |
|---|---|---|
| Auto-configuration overview | `TraceGraphAutoConfiguration` | 🔶 Partial — [Spring Boot](spring-boot.md) covers basics; no dedicated concept page explaining each conditional. |
| LLM auto-config | `LlmAutoConfiguration`, `tracegraph.llm.*` properties | ❌ TODO: needs page |
| Memory auto-config | `MemoryAutoConfiguration`, `tracegraph.memory.jdbc.*` properties | ❌ TODO: needs page |
| Trace web endpoints auto-config | `TraceWebAutoConfiguration`, `tracegraph.web.enabled` | ❌ TODO: needs page |
| Replay REST endpoint | `TraceReplayController` | ❌ TODO: needs page |

**Recommended page:** expand [Spring Boot](getting-started/spring-boot.md) with a section per auto-config, or add `reference/auto-configuration.md`.

---

## Priority Summary

The table below ranks missing pages by impact (breadth of TraceGraph capability exposed × user demand based on peer framework traffic).

| Priority | Missing page | Effort |
|---|---|---|
| P1 | `concepts/time-travel.md` | Medium — material exists in Tutorial 10; needs concept framing. |
| P1 | `concepts/streaming.md` | Medium — no existing page at all. |
| P1 | `concepts/breakpoints.md` | Low — material exists in Tutorial 11 and HITL cookbook. |
| P1 | `concepts/multi-agent.md` | Medium — subgraphs + supervisor + handoff in one page. |
| P2 | `concepts/persistence.md` | Medium — consolidates `CheckpointStore` + `MemoryStore`. |
| P2 | `concepts/map-reduce.md` | Low — material exists in Tutorial 05. |
| P2 | `concepts/retry-policy.md` | Low — material exists in Tutorial 03. |
| P3 | `concepts/routing-node.md` | Low — one class to explain. |
| P3 | `concepts/cost-tracking.md` | Low — covers `LlmCostListener` + `TokenBudgetListener`. |
| P3 | `concepts/visualization.md` | Low — two methods to demonstrate. |
| P3 | `concepts/eval-harness.md` | Low — new `tracegraph-eval` module. |
| P3 | `concepts/structured-output.md` | Low — one class in connectors. |
| P4 | `concepts/double-texting.md` | Low — mostly a "not applicable + workaround" page. |
| P4 | `reference/auto-configuration.md` | High — many conditionals to document. |
