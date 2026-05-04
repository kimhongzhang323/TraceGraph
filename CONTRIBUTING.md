# Contributing to TraceGraph

Thanks for your interest. TraceGraph aims to be a production-grade JVM agent runtime, so contributions are evaluated against reliability, debuggability, and clean public-API boundaries.

## Ground rules

- **JDK 21+**. We use records, pattern matching, virtual threads.
- **Maven multi-module.** Run `mvn -B test` from the repo root before opening a PR.
- **Compiler args:** `-Xlint:all -Werror`. Warnings break the build.
- **Tests cover behavior, not implementation.** JUnit 5 + AssertJ.
- **No comments unless the WHY is non-obvious.** Identifier names should carry the WHAT.

## Module boundaries

`tracegraph-core` is SLF4J-only — no Spring, Jackson, or OTel imports there. SPI types live in `core/spi`, implementations live in the downstream modules:

| Concern | Module |
|---|---|
| OTel, replay, trace diff | `tracegraph-observability` |
| Memory store implementations | `tracegraph-memory` |
| Spring auto-config and REST | `tracegraph-spring-boot-starter` |
| LLM and vector adapters | `tracegraph-connectors` |

If a feature would force `tracegraph-core` to depend on a heavy library, it belongs in another module.

## API design

Read [`.claude/rules/api-design.md`](.claude/rules/api-design.md) before changing public types. Highlights:

- Pre-1.0 minors may break, but every break lands in `CHANGELOG.md`.
- `Builder` is a static nested class on the type it builds; `Type.builder()` is the entry point.
- Records are frozen contracts — adding a component breaks every positional consumer.
- Public exceptions are `RuntimeException` subclasses with `(message)` and `(message, cause)` constructors.

## Concurrency

Read [`.claude/rules/concurrency.md`](.claude/rules/concurrency.md). We target Loom; avoid `synchronized` across blocking I/O, avoid `ThreadLocal` in node paths, prefer `final` and immutable collections at module boundaries.

## Submitting

1. Fork and create a topic branch off `main`.
2. Keep commits focused; rebase rather than merge `main` into your branch.
3. Run `mvn -B test`. New behavior needs new tests.
4. Update `CHANGELOG.md` under `[Unreleased]`.
5. Open a PR against `main`. Describe the user-visible change and any API impact.

## Reporting bugs

Open a GitHub issue with:
- TraceGraph version
- JDK version (`java -version`)
- A minimal reproducer (preferably a failing test)

Security issues: see [`SECURITY.md`](SECURITY.md).
