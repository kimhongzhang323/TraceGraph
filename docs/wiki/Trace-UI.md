# Trace UI

The Trace UI is a browser-based dashboard for inspecting, replaying, and comparing agent executions. It reads directly from the TraceGraph REST API — there is no separate backend to deploy. Served by the `tracegraph-ui` module.

> 🌐 中文版： **[[zh-Trace-UI|追踪界面]]**

## Prerequisites

1. **`tracegraph-spring-boot-starter`** on the classpath.
2. **A `Graph<?>` bean** in your Spring context.
3. **A `TraceStore` bean** — `InMemoryTraceStore` for dev; `JsonFileTraceStore` or `JdbcTraceStore` for production.
4. **`tracegraph-observability`** on the classpath (provides `TraceStore`, `RecordingTraceRecorder`, …).

## Enabling and accessing

The UI is enabled by default when the conditions above are met; `TraceUiAutoConfiguration` serves the static assets and API routes. Disable with:

```yaml
tracegraph:
  ui:
    enabled: false
```

Open it at:

```
http://localhost:8080/tracegraph/ui/
```

It connects to the REST API at the same origin (no CORS config needed). You can also use the REST endpoints directly without the UI — see **[[REST API Reference]]**.

## The four views

| View | URL fragment | Backed by |
|---|---|---|
| Trace list | `#/traces` | `GET /tracegraph/traces` |
| Trace detail | `#/traces/:id` | `GET /tracegraph/traces/{id}` + replay/resume |
| Graph structure | `#/graph` | `GET /tracegraph/ui/graph` |
| Diff view | `#/diff` | `GET /tracegraph/traces/{a}/diff/{b}` |

### Trace list (`#/traces`)

The home screen — every stored execution as a row: **execution ID** (click to open), **status** (`COMPLETED` / `FAILED` / `INTERRUPTED` / `RUNNING`), **started/completed** timestamps, and **node count**. Pagination uses Previous/Next and a page size; the `X-Total-Count` header drives the page count. A search box filters by partial id or status client-side.

### Trace detail (`#/traces/:id`)

Full step-by-step history for one trace. Each `TraceStep` row shows the **node name**, **attempts**, **token usage** (LLM nodes), and a success/failure icon. Click a row to expand the **state diff panel** — a two-column JSON diff of before/after with changed fields highlighted (the primary tool for understanding what a node did).

- **Replay from here** on any step → `POST /tracegraph/traces/{id}/replay?step=N`; the new execution id appears in a toast and the forked trace shows `forkedFrom` metadata.
- **Resume** appears when status is `INTERRUPTED` → `POST /tracegraph/traces/{id}/resume`; the view polls until the run completes or fails.

### Graph structure (`#/graph`)

Renders the compiled graph as an interactive DAG. **Nodes** are labelled rectangles (entry marked with an arrow-in icon, terminals with a double border); **edges** are directed arrows (conditional edges show a predicate badge); **subgraphs** are collapsible labelled clusters; **parallel groups** are a dashed box around the anonymous branches. Hover a node for its retry policy; click a node to jump to its most recent trace step.

### Diff view (`#/diff`)

Compares two traces side by side. Pick a **left** and **right** trace (or paste ids) and click **Compare**. Renders the **matched prefix** (identical steps by node name + before/after state), the **divergence point** (first differing step, highlighted), the **left/right remainders** after divergence, and **summary badges** (same status? same final state? identical?). Use it to compare a baseline against a replay to see the impact of a prompt or graph change.

---

**Related:** **[[REST API Reference]]** · **[[Observability and Replay]]** · **[[Graph Complexity]]**
