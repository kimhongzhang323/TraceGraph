# Trace UI — Views Guide

The Trace UI has four views accessible via the top navigation bar. This page describes what each view shows and how to use it.

---

## Trace List (`#/traces`)

The trace list is the home screen. It shows every stored execution as a table row with:

- **Execution ID** — click to open the detail view.
- **Status** — `COMPLETED`, `FAILED`, `INTERRUPTED`, or `RUNNING`.
- **Started at** / **Completed at** — ISO timestamps.
- **Node count** — number of steps recorded.

**Pagination** — use the Previous / Next buttons or set the page size to control how many rows are loaded at a time. The `X-Total-Count` response header drives the page count display.

**Filtering** — type a partial execution ID or status into the search box to narrow results client-side.

---

## Trace Detail (`#/traces/:id`)

The detail view shows the full step-by-step execution history for one trace.

### Step list

Each row is a `TraceStep` with:

- **Node name** — the node that produced this step.
- **Attempts** — how many times the node was retried before succeeding (or failing).
- **Token usage** — prompt and completion tokens for LLM nodes.
- **Status icon** — green tick for success, red X for failure.

Click a row to expand the **state diff panel**.

### State diff panel

Shows the before/after state for the selected step as a two-column JSON diff. Changed fields are highlighted. This is the primary tool for understanding what a node did to the state.

### Replay from step

Each step row has a **Replay from here** button. Clicking it sends:

```
POST /tracegraph/traces/{id}/replay?step=N
```

The new execution ID appears in a toast notification and the trace list refreshes. The forked trace shows `forkedFrom` metadata in its detail view.

### Resume (interrupted runs)

When the trace status is `INTERRUPTED`, a **Resume** button appears at the top of the detail view. Clicking it sends:

```
POST /tracegraph/traces/{id}/resume
```

The view polls for the updated status until the run completes or fails.

---

## Graph Structure (`#/graph`)

The graph view renders the compiled graph as an interactive directed acyclic graph (DAG).

- **Nodes** are rectangles labelled with their name. Entry nodes are marked with an arrow-in icon; terminals with a double border.
- **Edges** are directed arrows. Conditional edges show a predicate badge.
- **Subgraphs** are rendered as labelled clusters (collapsible).
- **Parallel groups** are shown as a dashed box containing the anonymous branches.

Hover over a node to see its retry policy (if any). Click a node name to jump to the most recent trace step for that node in the last execution.

The graph data comes from `GET /tracegraph/ui/graph`.

---

## Diff View (`#/diff`)

The diff view compares two execution traces side by side.

1. Select the **left trace** and **right trace** from the dropdowns (or paste execution IDs).
2. Click **Compare**.

The view calls `GET /tracegraph/traces/{a}/diff/{b}` and renders:

- **Matched prefix** — steps that are identical in both traces (node name + before/after state).
- **Divergence point** — the step index where the two traces first differ, highlighted in orange.
- **Left remainder** — steps that only exist in the left trace after divergence.
- **Right remainder** — steps that only exist in the right trace after divergence.
- **Summary badges** — same status? same final state? identical?

Use the diff view to compare a baseline execution against a replay to understand the impact of a prompt change or graph modification.
