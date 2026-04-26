# API design rules

These rules govern public API in TraceGraph modules. They exist because the project's positioning is "production-grade JVM" — consumers will pin versions, depend on binary compat, and complain loudly when we break them.

## Semantic versioning

We follow semver strictly once we hit `1.0.0`. Until then (`0.x.y`), minor bumps may break — but we still document every break in `CHANGELOG.md`.

| Change | Pre-1.0 | Post-1.0 |
|---|---|---|
| New module / new public type | minor | minor |
| New `default` method on interface | patch | minor |
| New abstract method on interface | minor (breaks consumers) | major |
| Removed/renamed public type, method, field | minor | major |
| Generic-parameter changes on public types | minor | major |
| Adding a record component | minor | major |
| Tightening a return type (covariant) | patch | minor |
| Loosening a parameter type (contravariant) | patch | minor |
| Changing `@FunctionalInterface` SAM signature | minor | major |

## Breaking-change policy

- Never break an API in a patch release.
- Deprecate first (`@Deprecated(since = "0.x")`) for at least one minor before removal.
- For removals, use `@Deprecated(forRemoval = true)`.

## Public surface checklist

Before marking a class `public`:

1. Does a consumer outside the module need to instantiate or reference it directly? If no → package-private.
2. Could it be hidden behind a static factory on a stable type? (e.g., hide `SimpleContext` behind `Context`.)
3. Is there an SPI version of the same concept? Don't expose two contracts for the same thing.

## Generics

- Single-parameter generic types preferred. Two type parameters double the inference burden on builders.
- Don't use wildcards (`? extends`, `? super`) in fluent-builder return types — kills chain inference.
- Bounded type parameters (`<S extends State>`) lock consumers in. Avoid unless you actually need the bound.

## Records

- Records are frozen contracts. Adding a component breaks every positional consumer.
- Prefer records for small immutable value carriers (`Edge`, `ExecutionResult`).
- Don't use records for things that need an evolving API — use a sealed interface + classes.

## Builder conventions

- `Builder` is a static nested class on the type it builds.
- Constructed via `Type.builder()` static factory.
- All setters return `Builder<S>` (this), never `Builder<? extends S>`.
- `build()` validates eagerly and throws `*ValidationException` on bad input.
- Builders are not thread-safe; document if that ever changes.

## Exception design

- Public exceptions are `RuntimeException` subclasses unless we genuinely want callers to handle them locally (rare).
- Exception names end in `Exception`, not `Error`.
- Always provide `(String message)` and `(String message, Throwable cause)` constructors when subclassing.
