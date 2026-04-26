---
name: release-version
description: Use when bumping the project version (e.g. 0.1.0-SNAPSHOT → 0.1.0, then → 0.2.0-SNAPSHOT) and updating CHANGELOG.md. Triggers on user requests like "cut a release", "bump to 0.x", "tag 0.x".
---

# Release version skill

Bumps the version in the parent POM (modules inherit) and updates `CHANGELOG.md` with entries from `git log` since the last tag.

## Inputs

- `target_version` — e.g. `0.1.0` (release) or `0.2.0-SNAPSHOT` (next dev)

## Steps

1. **Verify clean working tree.** `git status` must be empty. If not, abort and ask.
2. **Verify tests pass.** `mvn -B test` from project root. If failing, abort.
3. **Bump version.** Use `mvn versions:set -DnewVersion=<target_version> -DprocessAllModules -DgenerateBackupPoms=false`. This updates the parent and every child module that uses `${project.version}`.
4. **Update CHANGELOG.md.**
   - If `CHANGELOG.md` doesn't exist, create it with a `# Changelog` header and the [Keep a Changelog](https://keepachangelog.com) format.
   - Find the last release tag: `git describe --tags --abbrev=0 2>/dev/null`. If none, use the initial commit.
   - Collect commits since that ref: `git log <last>..HEAD --pretty=format:'- %s'`.
   - Group under `### Added`, `### Changed`, `### Fixed`, `### Removed`. Use the conventional-commits prefix if present (`feat:` → Added, `fix:` → Fixed, `BREAKING CHANGE:` → Changed/Removed).
   - Add a new section: `## [<target_version>] - YYYY-MM-DD`.
5. **Run the build once more.** `mvn -B test` — make sure the version bump didn't break anything.
6. **Stage and report.** `git add -A`, then show the user the staged diff. Do NOT commit or tag automatically — that's a user decision.

## What this skill does NOT do

- Does not push tags
- Does not publish to Maven Central
- Does not write release notes for users (different from CHANGELOG)

## Conventions

- Snapshot versions end in `-SNAPSHOT`
- Release versions are bare (`0.1.0`, not `0.1.0-RELEASE`)
- After releasing `X.Y.Z`, immediately bump to `X.(Y+1).0-SNAPSHOT` for ongoing dev (unless user says otherwise)
