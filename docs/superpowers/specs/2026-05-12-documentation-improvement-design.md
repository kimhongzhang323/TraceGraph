---
title: TraceGraph Documentation Improvement
date: 2026-05-12
status: approved
---

# Documentation Improvement Design

## Scope

Rewrite and expand **all 14 module README files** (EN + ZH) across 6 modules, update the **root README.md + README.zh.md**, and enrich the **docs/site/docs** tutorial and getting-started pages.

Files in scope:
- `README.md` + `README.zh.md` (root)
- `tracegraph-core/README.md` + `README.zh.md`
- `tracegraph-runtime/README.md` + `README.zh.md`
- `tracegraph-memory/README.md` + `README.zh.md`
- `tracegraph-observability/README.md` + `README.zh.md`
- `tracegraph-connectors/README.md` + `README.zh.md`
- `tracegraph-spring-boot-starter/README.md` + `README.zh.md`
- `docs/site/docs/getting-started/*.md` (installation, quickstart, first-graph, spring-boot)
- `docs/site/docs/tutorial/*.md` (01–11)

## Standard Section Template (per module)

Every module README (EN and ZH) must contain the following sections, in this order:

1. **Header** — badge row + 1-line description
2. **What it does** — 3–5 sentence purpose and problem statement
3. **System context diagram** — Mermaid `graph LR` block showing where this module sits in the full 6-module system, highlighting this module
4. **Internal architecture diagram** — Mermaid `graph TD` or `classDiagram` showing the module's own key types, SPIs, and implementations
5. **State / lifecycle diagram** — Mermaid `stateDiagram-v2` for modules with meaningful state transitions (runtime, observability); omit for purely structural modules
6. **Sequence diagram** — Mermaid `sequenceDiagram` showing multi-actor interaction (node ↔ executor ↔ store ↔ listener, etc.)
7. **Data model / ER diagram** — Mermaid `erDiagram` for modules with persistent stores (memory, observability, runtime)
8. **Core concepts** — key public types with 1-sentence role descriptions and annotated code snippets
9. **Complete usage walkthrough** — numbered steps with full working Java code
10. **Configuration reference** — markdown table of properties (especially for runtime and starter)
11. **Integration with other modules** — short prose + code showing how to compose this module with its siblings
12. **Testing guidance** — how to write unit/integration tests against this module in isolation
13. **FAQ** — 3–5 common questions with concise answers

Chinese (ZH) versions are full translations matching the English depth — not stubs or partial translations.

## Root README Additions

The root `README.md` and `README.zh.md` receive these new sections (in addition to existing content):

- **Full system architecture diagram** — single Mermaid `graph TD` showing all 6 modules, their SPIs, and the data/control flow between them
- **Module dependency graph** — Mermaid `graph LR` showing compile-time module deps (core ← runtime ← starter, etc.)
- **Comparison table** — TraceGraph vs LangGraph4j vs Temporal vs Spring Batch (feature rows: typed state, retries, checkpoints, replay, OTel, LLM adapters, Spring Boot starter, license)
- **Feature matrix** — each module as a column, major features as rows, ✅/❌/🔶 cells

## Visualization Types per Module

| Module | Diagrams included |
|---|---|
| `tracegraph-core` | System context, class diagram (Node/Edge/Graph/SPIs), flowchart (execution loop), sequence (node enter/exit/listener) |
| `tracegraph-runtime` | System context, class diagram (CheckpointStore impls), state machine (RUNNING→INTERRUPTED→RESUMED→COMPLETED), sequence (checkpoint write + resume), parallel fan-out flowchart |
| `tracegraph-memory` | System context, class diagram (MemoryStore SPI + 3 impls), ER diagram (JDBC table), sequence (ctx.memory() call chain) |
| `tracegraph-observability` | System context, class diagram (TraceStore/TraceRecorder/Replayer/TraceDiff), state machine (trace lifecycle), sequence (record→store→replay→diff), ER diagram (JDBC trace table) |
| `tracegraph-connectors` | System context, class diagram (LlmClient SPI + adapters + ChatNode + ReActAgent), state machine (ReAct loop), sequence (ChatNode → LlmClient → ToolCall → ToolResult) |
| `tracegraph-spring-boot-starter` | System context, class diagram (AutoConfigurations + conditional beans), sequence (Spring startup → bean registration → user Graph injection), flowchart (conditional bean resolution logic) |

## Execution Order (Domain Clusters)

Work proceeds in three parallel agent clusters, then root README last:

**Cluster 1** (parallel): `tracegraph-core` EN+ZH + `tracegraph-runtime` EN+ZH
**Cluster 2** (parallel): `tracegraph-memory` EN+ZH + `tracegraph-observability` EN+ZH
**Cluster 3** (parallel): `tracegraph-connectors` EN+ZH + `tracegraph-spring-boot-starter` EN+ZH
**Final**: Root README.md + README.zh.md (pulls from all modules)
**Bonus**: Enrich `docs/site/docs` tutorial pages inline with diagrams + cross-links

## Quality Criteria

- Each module README: minimum 400 lines (EN), minimum 400 lines (ZH)
- Every diagram uses valid Mermaid syntax (flowchart TD, sequenceDiagram, classDiagram, stateDiagram-v2, erDiagram)
- Chinese text is natural Mandarin — not machine-literal translation
- Code examples compile against the actual public API (verify against source)
- No placeholder text ("TODO", "TBD", "...")
- Internal consistency: module descriptions in root README match module-level READMEs
