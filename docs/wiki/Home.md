# TraceGraph Wiki

**A production-grade agent runtime for the JVM.** Typed graphs, durable memory, deep observability.

TraceGraph is a JVM-native agent runtime for building typed execution graphs with durable state, retries, checkpoints, memory, and observability hooks. It is aimed at teams that want graph-style orchestration on the JVM without giving up strong typing, testability, or production control. It is **not** trying to be a line-by-line clone of LangGraph — the focus is reliability, debuggability, and clean integration with Java and Spring ecosystems.

> **Current release:** `0.3.0` (2026-05-24) · **Requires:** JDK 21 · **License:** Apache 2.0

> 🌐 **Languages:** English (default) · **[[中文|zh-Home]]**

---

## Start here

| If you want to… | Go to |
|---|---|
| Install and run your first graph | **[[Getting Started]]** |
| Work through a guided, build-up walkthrough | **[[Tutorial]]** |
| Copy-paste solutions to concrete problems | **[[Cookbook]]** |
| Understand graphs, nodes, edges, state | **[[Core Concepts]]** |
| Know exactly how a run executes | **[[Execution Model]]** |
| Understand the design rationale | **[[Architecture]]** |
| See every module and what it's for | **[[Modules]]** |

## Capabilities

| Area | Page |
|---|---|
| Retries, async, parallel, checkpoints, resume, interrupts, subgraphs, routing, streaming | **[[Runtime Features]]** |
| Scoped cross-run key-value memory (in-memory, file, JDBC) | **[[Memory]]** |
| OpenTelemetry, trace recording, replay, diff, cost tracking | **[[Observability and Replay]]** |
| What a recorded trace looks like (JSON, diff, cost) | **[[Sample Trace Output]]** |
| LLM clients (OpenAI, Anthropic, …), ChatNode, ReAct | **[[LLM Connectors]]** |
| Handoff, group chat, voting, role/tool isolation | **[[Multi-Agent Patterns]]** |
| Retrieval-augmented generation, vector stores | **[[RAG]]** |
| Auto-configuration, REST endpoints, DI | **[[Spring Boot Integration]]** |
| Golden-trace replay, BLEU/ROUGE/F1, CI gating | **[[Evaluation]]** |
| Every `/tracegraph/*` HTTP endpoint | **[[REST API Reference]]** |
| Browser dashboard for traces & replay | **[[Trace UI]]** |
| Structural metrics for a compiled graph | **[[Graph Complexity]]** |
| Common questions | **[[FAQ]]** |

---

## Why TraceGraph

- Typed graph definitions with plain Java functions
- Deterministic execution paths and explicit state transitions
- Built-in support for retries, async nodes, and parallel fan-out
- Checkpointing and resume hooks for long-running flows
- Trace recording and replay support for debugging
- OpenTelemetry integration for production observability
- Spring Boot auto-configuration for application integration
- Connector modules for LLM-style adapters on the JVM

**Good fit when you want:** graph-shaped orchestration with explicit node boundaries, predictable state transitions that stay easy to test, production hooks (retries, checkpoints, trace replay, OpenTelemetry), and a JVM-first library friendly to ordinary Java and Spring idioms.

**Probably not the right fit when you want:** a no-code orchestration UI, hidden control flow driven mainly by prompts instead of application code, or a batteries-included hosted platform that owns runtime, storage, and tracing for you.

## The killer differentiator

> **Replay any agent execution with a full state diff and reasoning trace.**

Plug in a `TraceRecorder`, run a graph, and you can step through every node's before/after state, diff two runs, and re-execute from any step against a modified graph. See **[[Observability and Replay]]**.

---

## A 30-second taste

```java
record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
    OrderState withValid(boolean v)   { return new OrderState(id, v, charged, shipped); }
    OrderState withCharged(boolean v) { return new OrderState(id, valid, v, shipped); }
    OrderState withShipped(boolean v) { return new OrderState(id, valid, charged, v); }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge",   (state, ctx) -> state.withCharged(true))
        .node("ship",     (state, ctx) -> state.withShipped(true))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)
        .edge("charge", "ship")
        .terminal("ship")
        .build();

ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));
```

There is no hidden scheduler or opaque agent loop behind `Graph.run(...)`. **The graph definition is the control flow.**

---

## Project status

TraceGraph is under active development (pre-1.0). Expect breaking changes between `0.x` releases; every break is documented in [`CHANGELOG.md`](https://github.com/kimhongzhang323/TraceGraph/blob/main/CHANGELOG.md).

- `tracegraph-core` is the most mature module.
- `tracegraph-runtime`, `tracegraph-memory`, `tracegraph-observability`, `tracegraph-spring-boot-starter` are implemented and tested but still evolving.
- `tracegraph-connectors` adds first-class multi-agent patterns on top of ReAct + Supervisor primitives.
- `tracegraph-eval` covers golden-trace replay, baseline comparison, and text metrics.

> ℹ️ This wiki mirrors the in-repo docs (`README.md`, `docs/site/docs`, and per-module READMEs). When in doubt, the release artifacts are the real compatibility boundary.
