# Full CI/CD Pipeline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire five missing CI/CD gaps into GitHub Actions: quality gate (SpotBugs + license + Revapi), JaCoCo coverage reporting to Codecov, E2E test job, SNAPSHOT publishing to GitHub Packages, and GitHub Pages deployment for docs.

**Architecture:** All changes are GitHub Actions YAML workflows or Maven POM additions. Four new workflow files, one extended workflow, and two additions to the root `pom.xml` (a `coverage` Maven profile and a `distributionManagement` block). No application source code changes.

**Tech Stack:** GitHub Actions, Maven 3.x, JDK 21 (Temurin), JaCoCo 0.8.12, Codecov Action v5, GitHub Packages, GitHub Pages

---

## File Map

| File | Change |
|---|---|
| `pom.xml` | Add `jacoco-maven-plugin.version` property, JaCoCo in `pluginManagement`, `coverage` profile, `distributionManagement` block |
| `.github/workflows/quality.yml` | Create — SpotBugs + license + Revapi |
| `.github/workflows/coverage.yml` | Create — JaCoCo + Codecov upload |
| `.github/workflows/e2e.yml` | Create — tracegraph-e2e on PR + main |
| `.github/workflows/snapshot.yml` | Create — SNAPSHOT → GitHub Packages |
| `.github/workflows/docs.yml` | Extend — add `deploy` job for GitHub Pages |

---

## Task 1: Add JaCoCo to pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add JaCoCo version property**

In `pom.xml`, inside the `<properties>` block after the existing quality plugin versions (around line 95), add:

```xml
        <jacoco-maven-plugin.version>0.8.12</jacoco-maven-plugin.version>
```

- [ ] **Step 2: Add JaCoCo to pluginManagement**

In `pom.xml`, inside `<build><pluginManagement><plugins>`, after the `license-maven-plugin` entry (before the closing `</plugins>`), add:

```xml
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>${jacoco-maven-plugin.version}</version>
                </plugin>
```

- [ ] **Step 3: Add `coverage` profile**

In `pom.xml`, inside `<profiles>`, after the closing `</profile>` of the `quality` profile (around line 560) and before the closing `</profiles>`, add:

```xml
        <profile>
            <id>coverage</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.jacoco</groupId>
                        <artifactId>jacoco-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>prepare-agent</id>
                                <goals>
                                    <goal>prepare-agent</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>report</id>
                                <phase>verify</phase>
                                <goals>
                                    <goal>report</goal>
                                </goals>
                                <configuration>
                                    <excludes>
                                        <exclude>io/tracegraph/demo/**</exclude>
                                        <exclude>io/tracegraph/bench/**</exclude>
                                    </excludes>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
```

- [ ] **Step 4: Add `distributionManagement` block**

In `pom.xml`, after the closing `</issueManagement>` tag (around line 44) and before `<modules>`, add:

```xml
    <distributionManagement>
        <snapshotRepository>
            <id>github</id>
            <url>https://maven.pkg.github.com/kimhongzhang323/TraceGraph</url>
        </snapshotRepository>
    </distributionManagement>
```

- [ ] **Step 5: Verify profiles list**

Run:
```bash
mvn help:all-profiles -q
```

Expected output includes: `coverage`, `quality`, `api-check`, `release`, `security`, `mutation`, `sbom`, `docs`

- [ ] **Step 6: Verify coverage profile runs without error**

Run:
```bash
mvn -B -ntp -Pcoverage verify -pl tracegraph-core
```

Expected: BUILD SUCCESS, `tracegraph-core/target/site/jacoco/jacoco.xml` exists.

- [ ] **Step 7: Commit**

```bash
git add pom.xml
git commit -m "build: add JaCoCo coverage profile and GitHub Packages distributionManagement"
```

---

## Task 2: Create `quality.yml`

**Files:**
- Create: `.github/workflows/quality.yml`

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/quality.yml` with this content:

```yaml
name: Quality

on:
  push:
    branches:
      - main
      - master
  pull_request:

concurrency:
  group: quality-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  spotbugs:
    name: SpotBugs + license
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up Temurin JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Run SpotBugs and license check
        run: mvn -B -ntp -Pquality verify -DskipTests

      - name: Upload SpotBugs report
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: spotbugs-report
          path: |
            **/target/spotbugsXml.xml
            **/target/spotbugs.xml
          if-no-files-found: ignore

  api-compat:
    name: API compatibility (Revapi)
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up Temurin JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Check API compatibility
        run: mvn -B -ntp -Papi-check verify -DskipTests
```

- [ ] **Step 2: Validate YAML syntax**

Run:
```bash
python -c "import yaml, sys; yaml.safe_load(open('.github/workflows/quality.yml')); print('YAML OK')"
```

Expected: `YAML OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/quality.yml
git commit -m "ci: add quality gate — SpotBugs, license check, Revapi API compat"
```

---

## Task 3: Create `coverage.yml`

**Files:**
- Create: `.github/workflows/coverage.yml`

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/coverage.yml` with this content:

```yaml
name: Coverage

on:
  push:
    branches:
      - main
      - master
  pull_request:

concurrency:
  group: coverage-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  jacoco:
    name: JaCoCo + Codecov
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up Temurin JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Build and collect coverage
        run: mvn -B -ntp -Pcoverage verify

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v5
        with:
          files: >-
            tracegraph-core/target/site/jacoco/jacoco.xml,
            tracegraph-runtime/target/site/jacoco/jacoco.xml,
            tracegraph-memory/target/site/jacoco/jacoco.xml,
            tracegraph-observability/target/site/jacoco/jacoco.xml,
            tracegraph-connectors/target/site/jacoco/jacoco.xml,
            tracegraph-spring-boot-starter/target/site/jacoco/jacoco.xml,
            tracegraph-rag/target/site/jacoco/jacoco.xml
          fail_ci_if_error: false
          verbose: false
```

> **Note on `CODECOV_TOKEN`:** For public repos Codecov accepts uploads without a token. If the repo is private, add a `token: ${{ secrets.CODECOV_TOKEN }}` line under `with:` and create the secret in GitHub → Settings → Secrets.

- [ ] **Step 2: Validate YAML syntax**

Run:
```bash
python -c "import yaml, sys; yaml.safe_load(open('.github/workflows/coverage.yml')); print('YAML OK')"
```

Expected: `YAML OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/coverage.yml
git commit -m "ci: add JaCoCo coverage reporting with Codecov upload"
```

---

## Task 4: Create `e2e.yml`

**Files:**
- Create: `.github/workflows/e2e.yml`

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/e2e.yml` with this content:

```yaml
name: E2E

on:
  push:
    branches:
      - main
      - master
  pull_request:

concurrency:
  group: e2e-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  e2e:
    name: End-to-end tests
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up Temurin JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Install all modules to local repo
        run: mvn -B -ntp install -DskipTests

      - name: Run E2E tests
        run: mvn -B -ntp -pl tracegraph-e2e verify

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: e2e-reports
          path: |
            tracegraph-e2e/target/surefire-reports/*
            tracegraph-e2e/target/failsafe-reports/*
          if-no-files-found: ignore
```

- [ ] **Step 2: Validate YAML syntax**

Run:
```bash
python -c "import yaml, sys; yaml.safe_load(open('.github/workflows/e2e.yml')); print('YAML OK')"
```

Expected: `YAML OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/e2e.yml
git commit -m "ci: add E2E test job — runs tracegraph-e2e on every PR and main push"
```

---

## Task 5: Create `snapshot.yml`

**Files:**
- Create: `.github/workflows/snapshot.yml`

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/snapshot.yml` with this content:

```yaml
name: Publish SNAPSHOT

on:
  push:
    branches:
      - main
      - master

permissions:
  contents: read
  packages: write

jobs:
  snapshot:
    name: Publish SNAPSHOT to GitHub Packages
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up Temurin JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
          server-id: github
          server-username: GITHUB_ACTOR
          server-password: GITHUB_TOKEN

      - name: Skip if not a SNAPSHOT version
        id: version-check
        shell: bash
        run: |
          VERSION=$(mvn -B -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
          echo "version=$VERSION" >> "$GITHUB_OUTPUT"
          if [[ "$VERSION" != *-SNAPSHOT ]]; then
            echo "Not a SNAPSHOT ($VERSION) — skipping publish."
            echo "skip=true" >> "$GITHUB_OUTPUT"
          else
            echo "SNAPSHOT detected ($VERSION) — will publish."
            echo "skip=false" >> "$GITHUB_OUTPUT"
          fi

      - name: Publish to GitHub Packages
        if: steps.version-check.outputs.skip == 'false'
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}
        run: mvn -B -ntp deploy -DskipTests
```

- [ ] **Step 2: Validate YAML syntax**

Run:
```bash
python -c "import yaml, sys; yaml.safe_load(open('.github/workflows/snapshot.yml')); print('YAML OK')"
```

Expected: `YAML OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/snapshot.yml
git commit -m "ci: publish SNAPSHOT artifacts to GitHub Packages on main push"
```

---

## Task 6: Extend `docs.yml` with GitHub Pages deploy

**Files:**
- Modify: `.github/workflows/docs.yml`

- [ ] **Step 1: Replace `docs.yml` with extended version**

Replace the entire content of `.github/workflows/docs.yml` with:

```yaml
name: Docs

on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - 'mkdocs.yml'
      - 'docs/site/mkdocs.yml'
  pull_request:
    paths:
      - 'docs/**'
      - 'mkdocs.yml'
      - 'docs/site/mkdocs.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    name: Build docs
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0

      - uses: actions/setup-python@v6
        with:
          python-version: '3.12'

      - name: Install dependencies
        run: pip install -r docs/site/docs/requirements.txt

      - name: Build docs (strict)
        run: mkdocs build --strict --config-file docs/site/mkdocs.yml

      - name: Upload pages artifact
        if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
        uses: actions/upload-pages-artifact@v3
        with:
          path: docs/site/site

  deploy:
    name: Deploy to GitHub Pages
    needs: build
    if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

> **Prerequisite:** GitHub Pages must be enabled in the repo settings. Go to Settings → Pages → Source → select **GitHub Actions**. This only needs to be done once.

- [ ] **Step 2: Validate YAML syntax**

Run:
```bash
python -c "import yaml, sys; yaml.safe_load(open('.github/workflows/docs.yml')); print('YAML OK')"
```

Expected: `YAML OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/docs.yml
git commit -m "ci: deploy docs to GitHub Pages on main push"
```

---

## Post-Implementation Checklist

After all tasks are committed and pushed to a PR / merged to main:

- [ ] Verify `quality.yml` triggers on the PR — both `spotbugs` and `api-compat` jobs appear in the Checks tab
- [ ] Verify `coverage.yml` triggers — Codecov posts a PR comment with coverage delta
- [ ] Verify `e2e.yml` triggers — `tracegraph-e2e` tests run and pass
- [ ] Verify `snapshot.yml` triggers on merge to main — check GitHub Packages page for published artifacts
- [ ] Verify `docs.yml` deploys — GitHub Pages URL shows the MkDocs site after merge
- [ ] (Optional) Add `CODECOV_TOKEN` secret if repo is private and Codecov uploads fail
- [ ] (Optional) Enable GitHub Pages in repo Settings → Pages → Source → GitHub Actions
