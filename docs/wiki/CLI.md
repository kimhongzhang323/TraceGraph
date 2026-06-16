# Trace CLI

`tracegraph-cli` is an interactive terminal trace viewer for a running TraceGraph application. It is the keyboard-driven counterpart to the browser-based **[[Trace UI]]** — browse recorded traces, inspect per-step state diffs, and live-tail executions, all over the same REST/SSE endpoints the starter already exposes. Built on [JLine 3](https://github.com/jline/jline3); the only other dependency is Jackson.

## Prerequisites

A running TraceGraph app that serves the starter's `/tracegraph/*` endpoints:

1. **`tracegraph-spring-boot-starter`** on the classpath, with a `Graph<?>` bean and a `TraceStore` bean (see **[[Trace UI]]** for the full list).
2. For the **live tail**, a `LiveTraceFeed` wired as a `TraceRecorder` so `GET /tracegraph/stream` is registered (auto-configured when the bean is present).

The CLI itself runs anywhere with a JDK 21 runtime — it only needs network access to the app.

## Running

Build the runnable jar (or grab it from your build), then point it at the app:

```bash
java -jar tracegraph-cli/target/tracegraph-cli-<version>.jar \
     --url http://localhost:8080 [--api-key KEY]
```

| Flag | Default | Purpose |
|---|---|---|
| `--url` | `http://localhost:8080` | Base URL of the running TraceGraph app |
| `--api-key` | _(none)_ | Sent as `X-Api-Key` on every request (matches the starter's API-key filter) |
| `--help`, `-h` | | Print usage and exit |

If the app isn't reachable, the CLI shows an error screen ("is the TraceGraph app running and --url correct?") and any key retries.

## Interface tour

Three screens, navigated entirely by keyboard. The top line of every screen is an inverse-video header: `tracegraph · <screen>` on the left, the available keys on the right. Colors below are described in brackets; in a real terminal they are rendered with ANSI.

### 1. Trace list — the landing screen

```text
 tracegraph · traces                   ↑↓ move · ⏎ open · w watch live · r refresh · q quit

  #    EXECUTION                               STATUS        STEPS  STARTED
  ────────────────────────────────────────────────────────────────────────────────────────
▸ 0    checkout-7f3a91c2                       COMPLETED     4      2026-06-16T09:14:02Z
  1    summarize-bd0e1144                      FAILED        2      2026-06-16T09:18:41Z
  2    rag-query-9920aa07                      INTERRUPTED   3      2026-06-16T09:22:10Z
```

Every stored execution is a row: index, **execution id**, **status**, **step count**, and **start time**. The selected row is marked with a green `▸` and bold id. Status is color-coded — `COMPLETED` [green], `FAILED` [red], `INTERRUPTED`/`TERMINATED` [yellow], anything else [cyan]. Backed by `GET /tracegraph/traces` (+ one `GET /tracegraph/traces/{id}` per row for the summary).

| Key | Action |
|---|---|
| `↑` / `↓` | Move the selection |
| `⏎` | Open the selected trace (→ detail screen) |
| `w` | Watch the live tail (→ live screen) |
| `r` | Refresh the list from the server |
| `q` | Quit |

### 2. Trace detail — step-by-step, with state diff

Press `⏎` on a trace, then `s` to toggle the before/after state pane:

```text
 tracegraph · trace summarize-bd0e1144                ↑↓ step · s state · esc back · q quit
  FAILED  ·  started 2026-06-16T09:18:41Z  ·  2 steps

  #0   load-context
▸ #1   llm-answer                   ↻3 ✕

  state · before → after  (step #1)
  ────────────────────────────────────────────────────────────────────────────────────────
  - {
  -   "query" : "refund policy",
  -   "docs" : 3
  - }
  ! (no exit; node failed)
```

Each `TraceStep` is a row showing the **node name**, a yellow `↻N` badge when the node was retried (N attempts), and a red `✕` when it failed. With the state pane open, the selected step's **before** state is shown in gray (`-` lines) and its **after** state in green (`+` lines). When a node threw, the after side reads `! (no exit; node failed)` [red] instead. Backed by `GET /tracegraph/traces/{id}`.

| Key | Action |
|---|---|
| `↑` / `↓` | Move between steps |
| `s` | Toggle the before/after state pane |
| `esc` | Back to the trace list |
| `q` | Quit |

### 3. Live tail — executions as they run

Press `w` from the list to stream events from `GET /tracegraph/stream` (Server-Sent Events). New events append at the bottom and the view keeps the newest that fit the terminal height:

```text
 tracegraph · live                                                        esc back · q quit
  ● connected — events stream as nodes execute

  rag-query-99  START
  rag-query-99  ENTER     load-context
  rag-query-99  EXIT      load-context          3 docs
  rag-query-99  ENTER     llm-answer
  rag-query-99  RETRY     llm-answer            attempt 2 — 503
  rag-query-99  COMPLETE
```

The status dot is green `●` once connected (yellow while connecting). Each line is `executionId  TYPE  node  detail`, with the event type color-coded: `START` [magenta], `ENTER` [cyan], `EXIT`/`COMPLETE` [green], `RETRY` [yellow], `ERROR` [red]. The tail runs on a virtual thread; `esc` stops it and returns to the list, `q` quits.

## Cross-platform behavior

JLine handles ANSI/VT enablement, raw mode, and arrow-key decoding across **Windows, macOS, and Linux**, with a graceful fallback to a "dumb terminal" when there's no real TTY. On terminals whose output charset isn't UTF-8 — chiefly legacy Windows consoles (cp1252/cp437) that would render Unicode box-drawing glyphs as `?` — the CLI detects this at startup and swaps to an ASCII glyph set so the layout stays legible:

```text
 tracegraph - traces             updn move - enter open - w watch live - r refresh - q quit
  #    EXECUTION                               STATUS        STEPS  STARTED
  ----------------------------------------------------------------------------------------
> 0    checkout-7f3a91c2                       COMPLETED     4      2026-06-16T09:14:02Z
  1    summarize-bd0e1144                      FAILED        2      2026-06-16T09:18:41Z
  2    rag-query-9920aa07                      INTERRUPTED   3      2026-06-16T09:22:10Z
```

(`▸ → >`, `─ → -`, `↻ → x`, `✕ → x`, `↑↓ → up dn`, `⏎ → enter`.) UTF-8 terminals are unchanged.

---

**Related:** **[[Trace UI]]** · **[[REST API Reference]]** · **[[Observability and Replay]]**
