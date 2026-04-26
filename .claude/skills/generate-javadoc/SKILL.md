---
name: generate-javadoc
description: Use when generating, validating, or fixing Javadoc across TraceGraph modules. Triggers on user requests like "generate javadoc", "fix javadoc warnings", "publish docs", or after a public API change.
---

# Generate Javadoc skill

Builds Javadoc for all modules, fails on warnings, and reports gaps.

## Inputs

- `module` (optional) — limit to a single Maven module (e.g. `langgraph-core`). Default: all modules.

## Steps

1. **Run Javadoc with strict mode.**
   ```bash
   mvn -B javadoc:javadoc -Ddoclint=all -Dfailonwarning=true \
       ${module:+-pl $module}
   ```
   If the project doesn't yet declare `maven-javadoc-plugin` in the parent POM's `<pluginManagement>`, add it (version `3.10.1`) before running.

2. **Parse the output.** Surface:
   - Missing `@param` / `@return` / `@throws`
   - Broken `{@link}` / `{@code}` references
   - HTML / accessibility warnings
   - Untagged public types

3. **Auto-fix safe cases.**
   - Add minimal one-line Javadoc for any public type/method that has none, using the doc-writer agent's conventions (lead with role, not action).
   - Do NOT auto-write Javadoc that requires domain knowledge — flag those for the user.

4. **Re-run** until either zero warnings or only warnings flagged for human review remain.

5. **Aggregate the site.**
   ```bash
   mvn -B javadoc:aggregate -Ddoclint=all
   ```
   Output lands in `target/site/apidocs/`.

6. **Report.** Show the user:
   - Number of warnings before/after
   - Files auto-fixed
   - Files needing human Javadoc (with file:line)

## What this skill does NOT do

- Does not publish docs to a website
- Does not generate prose for non-obvious behavior — it asks the user

## Convention

- Public API in `langgraph-core` MUST have Javadoc (enforced via `failonwarning=true` in CI once added).
- Package-private types do not need Javadoc.
- Tests do not need Javadoc.
