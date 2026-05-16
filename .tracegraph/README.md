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

## Human responsibilities

- Review & merge (or close) `auto/*` PRs.
- Prune stale `auto/*` branches weekly.
- Edit `improvement-backlog.md` directly to inject high-priority work — top item wins.
- To pause the loop: delete the scheduled agent via the `schedule` skill.
