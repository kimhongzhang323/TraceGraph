# PR: i18n / Chinese docs expansion and CI preview

This pull request adds a comprehensive Chinese documentation site, module-level Chinese READMEs, expanded tutorials, example code, and a GitHub Actions workflow to build and publish a preview branch (`gh-pages-zh`).

Summary of changes
- Adds Chinese docs under `docs/site/docs/zh/` (tutorials, concepts, reference, cookbook).
- Adds/expands `README.zh.md` for repository and key modules (e.g., `tracegraph-core`).
- Adds runnable examples under `examples/quickstart` (Chinese variants and instructions).
- Adds CI workflow `.github/workflows/docs-zh-preview.yml` to build & publish the Chinese docs to `gh-pages-zh`.
- Normalizes translation terminology: uses **追踪** for `Trace` throughout Chinese docs.

Notes and guidance
- This PR's Chinese content was generated/expanded with AI assistance. Please review for tone, idiomatic phrasing, and technical accuracy.
- The docs site build requires Python + `mkdocs` + `mkdocs-material`. See `docs/site/mkdocs.yml` for config.

Docs preview / GitHub Actions permissions
- The included workflow attempts to publish using the repository `GITHUB_TOKEN` (default). Some repositories restrict the `GITHUB_TOKEN` push permissions; if you encounter a 403 from `peaceiris/actions-gh-pages`, create a Personal Access Token (PAT) with `repo` and `workflow` scopes and store it as the repository secret `GH_PAGES_PAT`.
- Repository settings may also need to allow `GitHub Actions` to create and push to `gh-pages-zh` or allow the `github-actions[bot]` to push. If your organization blocks `GITHUB_TOKEN` write access, using a PAT is the recommended workaround.

Testing locally
1. Install JDK 21 and Maven to build Java modules and runnable examples.
2. Install Python and mkdocs: `python -m pip install mkdocs mkdocs-material`.
3. Build site locally: `mkdocs build -f docs/site/mkdocs.yml -d site`.

Request for reviewers
- Native Chinese speakers: please review phrasing and translations, especially for domain terms and API examples.
- Module owners: please verify the accuracy of module-specific README.zh.md files.
- CI owner / repo admin: verify workflow permissions and advise whether a PAT is required.

Branch: `i18n/zh-autotranslate` → Target: `main`

---

（自动生成草稿已由作者扩写 — 请在合并前做人工校对。）
