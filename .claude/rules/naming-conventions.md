# Naming conventions

Standard Java conventions, with a few project-specific rules.

## Standard

- **Classes / records / interfaces / enums**: `PascalCase` — `Graph`, `NodeListener`, `ExecutionResult`, `Status`.
- **Methods / fields / variables / parameters**: `camelCase` — `executionId`, `runStep`, `maxSteps`.
- **Constants** (`static final`): `UPPER_SNAKE_CASE` — `DEFAULT_MAX_STEPS`, `LOG`.
- **Type parameters**: single uppercase letter when the meaning is generic — `<S>` for state, `<R>` for result, `<K, V>` for map-like.
- **Packages**: lowercase, no underscores — `io.tracegraph.core.exec`.

## Project-specific

- **Interface names** describe the role, not the implementation: `Node`, not `INode` or `NodeInterface`.
- **Functional interfaces**: name them after what they do, not what they are. `Node` (functional), not `NodeFunction`.
- **Records** carry data; name them as nouns: `Edge`, `ExecutionResult`. Not `EdgeRecord`, `EdgeData`.
- **Builders** are always `Type.Builder` — nested static class, not `TypeBuilder`.
- **Exceptions** end in `Exception` — `GraphValidationException`, `NodeExecutionException`. Never `Error` (reserved for VM-level).
- **SPI types** live under `*.spi` — `io.tracegraph.core.spi.NodeListener`. Internal types live under `*.exec` or `*.internal`.
- **Test classes** mirror the SUT: `Graph` → `GraphExecutionTest`, `GraphBuilderTest`. One test class per behavior cluster, not one test per method.
- **Test methods**: behavior in present tense, no `test` prefix — `rejectsDuplicateNodeName`, `firesEnterAndExitInOrder`. Not `testNodeNameValidation`.

## Avoid

- Hungarian notation: `strName`, `iCount`. No.
- Get/set on records: records auto-generate accessors named after the component. Don't add `getName()` to a record with a `name` component.
- "Manager", "Helper", "Utils" suffixes on stateful types — usually a sign the type does too much.
- "Impl" suffix on the only implementation of an interface. If there's only one impl, the interface might be unnecessary.

## File names

One public type per file, file name == type name. Package-private types may share a file in rare cases (closely related, small).
