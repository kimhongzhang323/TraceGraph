# CI/CD Pipeline — Full Design

**Date:** 2026-05-15  
**Status:** Approved  
**Scope:** Fill gaps in the existing GitHub Actions pipeline for TraceGraph

---

## Context

TraceGraph already has a solid CI/CD foundation:

| Workflow | What it does |
|---|---|
| `ci.yml` | Build + test on ubuntu/windows/macos, Javadoc doclint gate, JDK 25 EA allow-fail |
| `release.yml` | Maven Central publish (GPG signed), SBOM, GitHub Release |
| `security.yml` | Dependency review (PRs), CodeQL, OWASP dep-check, Gitleaks |
| `mutation.yml` | PIT mutation tests (nightly, 75% threshold on core) |
| `docs.yml` | MkDocs build (validates only, no deploy) |
| `release-drafter.yml` | Changelog automation |
| `dependabot.yml` | Dependency update PRs |

Maven profiles already defined but **never wired into CI**: `quality` (SpotBugs + license), `api-check` (Revapi), `coverage` (to be added).

---

## What We're Adding

### 1. `quality.yml` — Static analysis gate

**Triggers:** PR, push to `main`  
**Runner:** `ubuntu-latest`

Two jobs:

**`spotbugs`**
- Command: `mvn -B -ntp -Pquality verify -DskipTests`
- Runs SpotBugs (effort=Max, threshold=Medium) with FindSecBugs plugin + Apache license header check
- Uploads SpotBugs XML report as artifact on failure
- Uses existing `quality` Maven profile — no pom.xml changes needed

**`api-compat`**
- Command: `mvn -B -ntp -Papi-check verify -DskipTests`
- Runs Revapi to detect binary-breaking API changes against the last published release
- Uses existing `api-check` Maven profile — no pom.xml changes needed
- Graceful no-op until `0.1.0` is published (Revapi has nothing to compare against during `0.x`)

---

### 2. `coverage.yml` — Code coverage reporting

**Triggers:** PR, push to `main`  
**Runner:** `ubuntu-latest`

**pom.xml change:** Add a `coverage` profile that binds JaCoCo:
- `jacoco:prepare-agent` at `initialize` phase (instruments test runs)
- `jacoco:report` at `verify` phase (generates XML + HTML per module)
- Excludes `tracegraph-demo`, `tracegraph-bench`, `tracegraph-e2e` from reporting

**CI job:**
- Command: `mvn -B -ntp -Pcoverage verify`
- Uploads all `**/target/site/jacoco/jacoco.xml` to Codecov via `codecov/codecov-action@v5`
- No hard threshold — coverage surfaces as PR comment and badge only
- Rationale: PIT mutation tests (nightly, 75% threshold) provide a stronger correctness signal than line coverage thresholds

---

### 3. `e2e.yml` — End-to-end tests

**Triggers:** PR, push to `main`  
**Runner:** `ubuntu-latest`

Steps:
1. `mvn -B -ntp install -DskipTests` — installs all sibling modules to local repo so `tracegraph-e2e` can resolve them
2. `mvn -B -ntp -pl tracegraph-e2e verify` — runs E2E suite
3. Uploads Surefire/Failsafe reports as artifacts on failure

No external services required. No Testcontainers service containers needed.

---

### 4. `snapshot.yml` — SNAPSHOT publishing to GitHub Packages

**Triggers:** Push to `main` only  
**Runner:** `ubuntu-latest`  
**Environment:** none (uses built-in `GITHUB_TOKEN`)

Steps:
1. Version guard — reads POM version with `exec:exec`; exits 0 (skip) if version does NOT contain `-SNAPSHOT`
2. JDK setup with `server-id: github` so Maven authenticates to GitHub Packages automatically
3. `mvn -B -ntp deploy -DskipTests` — deploys all modules to GitHub Packages
4. No GPG signing (SNAPSHOTs don't require it; signing is release-only)

**pom.xml change:** Add `<distributionManagement>` block:
```xml
<distributionManagement>
  <snapshotRepository>
    <id>github</id>
    <url>https://maven.pkg.github.com/kimhongzhang323/TraceGraph</url>
  </snapshotRepository>
</distributionManagement>
```

Consumers add the GitHub Packages registry to their `pom.xml` to resolve SNAPSHOTs.

---

### 5. `docs.yml` — Docs deploy to GitHub Pages (extend existing)

**Current:** builds MkDocs on `main` push, never deploys.  
**Change:** add a second job `deploy` that runs after `build` succeeds, on `main` push only.

New permissions on the workflow:
```yaml
permissions:
  contents: read
  pages: write
  id-token: write
```

New `deploy` job:
- `needs: build`
- `if: github.ref == 'refs/heads/main'`
- Uses `actions/upload-pages-artifact` to package `docs/site/site/`
- Uses `actions/deploy-pages` to publish to GitHub Pages

PR pushes run `build` only — no deploy.

---

## Files Changed

| File | Change type |
|---|---|
| `.github/workflows/quality.yml` | New |
| `.github/workflows/coverage.yml` | New |
| `.github/workflows/e2e.yml` | New |
| `.github/workflows/snapshot.yml` | New |
| `.github/workflows/docs.yml` | Extend (add deploy job) |
| `pom.xml` | Add `coverage` profile + `distributionManagement` |

---

## What We're NOT Adding

- **SonarCloud** — requires external account/token setup; CodeQL already covers static analysis
- **Trivy** — OWASP dependency-check already covers CVEs; Trivy would be redundant
- **Checkstyle/PMD** — not configured in the existing pom.xml; out of scope
- **Multi-JDK matrix beyond 21 + 25-EA** — existing ci.yml already covers the meaningful range
- **Slack/email notifications** — no notification infrastructure in scope
- **Docker build** — no Dockerfile exists; demo module is a library, not a container
- **Manual approval gate** — Maven Central release already uses a GitHub `environment` (`maven-central`) which provides approval gating
- **Performance benchmark CI** — `tracegraph-bench` exists but benchmarks are inherently noisy in shared CI; keep as local/manual
