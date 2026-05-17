# `.tracegraph/` — auto-improvement contract

This directory drives a scheduled Claude Code agent that improves the repo on a 5-hour cadence.

## How it works

- **Cron**: `0 */5 * * *` (UTC), registered via the Claude Code `schedule` skill.
- **Each tick** the agent:
  1. `git fetch origin`, creates a worktree at `../tg-auto-<timestamp>` off `origin/main`.
  2. Reads `improvement-backlog.md`. Picks the topmost `- [ ]` item.
  3. Creates branch `auto/<slug>-<timestamp>`, implements the item (TDD, respects `CLAUDE.md` module boundaries, `-Werror`).
  4. Runs `mvn -B test`. On green: marks the backlog item `- [x]` in a second commit, pushes, opens a **draft PR against `main`** titled `[auto] <item>` describing the peer framework / weakness addressed.
  5. On red: leaves the PR draft with failing CI for human review (item stays `- [ ]`).

## Guardrails

- Never pushes to `main`, never force-pushes, never deletes branches.
- Never touches the user's currently checked-out branch — all work is in worktrees.
- 45-minute hard time budget per tick. WIP gets committed with `[WIP]` and the tick exits.
- Skips ticks when an `auto/*` PR has been open >24h without merge (prevents pileup).
- On merge conflict with `origin/main`: abandons the worktree; next tick retries.

## Backlog refill

When every item in `improvement-backlog.md` is checked, the agent:
- Reads the `research-angle:` marker at the top.
- Picks the next angle from the rotation: `developer-experience → observability → eval-harness → multi-agent-patterns → enterprise-ops → connectors-coverage → docs-and-onboarding → developer-experience…`
- Researches that angle (peer JVM/AI frameworks: LangGraph, LangChain4j, Spring AI, Semantic Kernel, CrewAI, AutoGen, Mastra, BAML, Haystack).
- Appends 5–10 new `- [ ]` items, updates the `research-angle:` marker, opens a PR from `auto/backlog-refresh-<timestamp>`.

## Coverage discipline

Backlog refill batches must collectively touch every major top-level module and folder over time — not just the historically active ones. The Plan subagent that produces refill items is required to:

1. **Tag every generated item with its target module**, surfaced in the agent tick log as:

   ```text
   coverage: <module>
   ```

   so humans can audit fairness directly from PR titles and logs.
2. **Skew recommendations toward modules untouched in the last 3 backlog refreshes.** Staleness is checked with:

   ```bash
   git log --since='15 days' -- <module>
   ```
3. **Cover `examples/` or `docs/site` at least once per refresh** whenever the research angle is `developer-experience` or `docs-and-onboarding`.

### Modules in rotation

- `tracegraph-core`
- `tracegraph-runtime`
- `tracegraph-observability`
- `tracegraph-memory`
- `tracegraph-connectors`
- `tracegraph-spring-boot-starter`
- `tracegraph-a2a`
- `tracegraph-bench`
- `tracegraph-eval`
- `tracegraph-ui`
- `examples/*`
- `docs/site`

### Why

Earlier refill batches over-concentrated on `tracegraph-observability` and `tracegraph-connectors`, while `tracegraph-a2a`, `tracegraph-ui`, `tracegraph-bench`, `examples/`, and `docs/site` were under-served. Inspired by LangGraph's "concept coverage matrix" approach — refill quality is measured by breadth across the surface area, not depth in one module.

## Human responsibilities

- Review & merge (or close) `auto/*` PRs.
- Prune stale `auto/*` branches weekly.
- Edit `improvement-backlog.md` directly to inject high-priority work — top item wins.
- To pause the loop: delete the scheduled agent via the `schedule` skill.
