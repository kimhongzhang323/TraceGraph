# Releasing TraceGraph

This document describes how to publish a TraceGraph release to Maven Central via the Sonatype Central Portal.

There are two paths: **release from CI** (recommended for `0.2.0` and later — push a tag, GitHub Actions does the rest) and **release from your laptop** (used for `0.1.0`). Both end at the same Central Portal draft awaiting your manual **Publish** click.

## Release from CI (recommended)

The `Release` workflow (`.github/workflows/release.yml`) triggers on tag push `v*` (and via `workflow_dispatch` for re-runs). It checks out the tag, sets up JDK 21, imports the GPG key, runs `mvn -P release deploy`, and creates a GitHub Release with auto-generated notes.

### One-time CI setup

In **Settings → Environments → maven-central**, add these secrets:

| Secret | Value |
|---|---|
| `CENTRAL_USERNAME` | Sonatype Central Portal token username |
| `CENTRAL_PASSWORD` | Sonatype Central Portal token password |
| `MAVEN_GPG_PRIVATE_KEY` | Output of `gpg --armor --export-secret-keys YOUR_KEY_ID` |
| `MAVEN_GPG_PASSPHRASE` | Passphrase for that GPG key |

The `maven-central` environment lets you add a manual approval step (recommended — `Settings → Environments → maven-central → Required reviewers`) so an unintended tag push doesn't auto-publish.

### Per-release flow

1. Bump POMs and CHANGELOG on a `release/<version>` branch:
   ```bash
   mvn -B versions:set -DnewVersion=0.2.0 -DgenerateBackupPoms=false
   # edit CHANGELOG.md: rename [Unreleased] → [0.2.0] - YYYY-MM-DD
   git commit -am "release: 0.2.0"
   git tag -a v0.2.0 -m "TraceGraph 0.2.0"
   ```
2. Open the PR, merge it (regular merge — preserves the tagged commit hash on `main`).
3. Pull main, verify the tag still points at the merged commit (re-tag if you squash-merged).
4. Push the tag — CI takes over:
   ```bash
   git push origin v0.2.0
   ```
5. Watch the **Release** workflow at `https://github.com/kimhongzhang323/TraceGraph/actions`. After it completes, validate and **Publish** the bundle at https://central.sonatype.com/publishing.
6. Open the next development version on a `chore/open-X.Y-snapshot` branch (`mvn versions:set` to `0.3.0-SNAPSHOT`, reopen `## [Unreleased]` in CHANGELOG, PR, merge).

## Release from your laptop (manual)

### One-time laptop setup

1. **Sonatype Central account** — register at https://central.sonatype.com and verify the `site.tracegraph` namespace (DNS verification on `tracegraph.site`). Maven groupId is reverse-DNS, so verifying the `tracegraph.site` domain unlocks publishing under `site.tracegraph.*`. Java package names (`io.tracegraph.core.*`) are independent of the Maven groupId and stay unchanged.
2. **GPG key** — generate one (`gpg --gen-key`) and publish it to a keyserver:
   ```bash
   gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
   ```
3. **Maven settings** — add to `~/.m2/settings.xml`:
   ```xml
   <servers>
     <server>
       <id>central</id>
       <username>${env.CENTRAL_USERNAME}</username>
       <password>${env.CENTRAL_PASSWORD}</password>
     </server>
   </servers>
   ```
   Generate the username/token from https://central.sonatype.com → "View Account".

### Per-release laptop steps

Use the `release-version` skill or follow this manually.

1. **Verify clean state**
   ```bash
   mvn -B -ntp verify
   git status   # clean
   ```

2. **Bump version** (drop `-SNAPSHOT`)
   ```bash
   mvn -B versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
   ```
   Update `CHANGELOG.md`: move `[Unreleased]` items under a new `[0.1.0] - YYYY-MM-DD` heading.

3. **Tag and commit**
   ```bash
   git commit -am "release: 0.1.0"
   git tag -a v0.1.0 -m "TraceGraph 0.1.0"
   ```

4. **Deploy** — runs sources, javadoc, GPG signing, uploads bundle to Central Portal:
   ```bash
   export CENTRAL_USERNAME=<token-name>
   export CENTRAL_PASSWORD=<token-value>
   export GPG_TTY=$(tty)
   mvn -B -ntp -P release -DskipTests deploy
   ```
   `autoPublish=false` is set in the parent POM — the bundle lands as a draft. Open https://central.sonatype.com/publishing to validate and publish.

5. **Push tag and main**
   ```bash
   git push origin main v0.1.0
   ```

6. **Open next development version**
   ```bash
   mvn -B versions:set -DnewVersion=0.2.0-SNAPSHOT -DgenerateBackupPoms=false
   git commit -am "chore: open 0.2.0-SNAPSHOT"
   git push
   ```

7. **GitHub Release** — at https://github.com/kimhongzhang323/TraceGraph/releases, draft from tag `v0.1.0`, paste the `[0.1.0]` CHANGELOG section as the body.

## Troubleshooting

- **GPG hangs in CI** — set `GPG_TTY` and pass `--pinentry-mode loopback` (already configured in the `release` profile). Provide the passphrase via `MAVEN_GPG_PASSPHRASE`.
- **`401 Unauthorized` on upload** — token, not password. Regenerate at central.sonatype.com.
- **Namespace not verified** — Central Portal blocks `site.tracegraph` until DNS verification on `tracegraph.site` completes.

## Smoke-test the release profile locally (no upload)

```bash
mvn -B -ntp -P release -Dgpg.skip=true -DskipTests verify
```

Confirms sources jar + javadoc jar build cleanly without exercising signing or upload.
