# TraceGraph

[![Maven Central](https://img.shields.io/maven-central/v/site.tracegraph/langgraph-core?label=Maven%20Central)](https://central.sonatype.com/artifact/site.tracegraph/langgraph-core)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

TraceGraph is a JVM-native agent runtime for building typed execution graphs with durable state, retries, checkpoints, memory, and observability hooks.

The project is aimed at teams that want graph-style orchestration on the JVM without giving up strong typing, testability, or production control. It is not trying to be a line-by-line clone of LangGraph. The focus here is reliability, debuggability, and clean integration with Java and Spring ecosystems.

## Why TraceGraph

- Typed graph definitions with plain Java functions
- Deterministic execution paths and explicit state transitions
- Built-in support for retries, async nodes, and parallel fan-out
- Checkpointing and resume hooks for long-running flows
- Trace recording and replay support for debugging
- OpenTelemetry integration for production observability
- Spring Boot auto-configuration for application integration
- Connector modules for LLM-style adapters on the JVM

## Project Status

TraceGraph is under active development.

- `langgraph-core` is the most mature module and already covers core graph construction and execution behavior.
- `langgraph-runtime`, `langgraph-memory`, `langgraph-observability`, and `langgraph-spring-boot-starter` are implemented and tested, but are still evolving.
- `langgraph-connectors` is intentionally early-stage and should be treated as experimental integration code.

Until the API settles, expect breaking changes between pre-1.0 releases.

## Modules

| Module | Purpose |
|---|---|
| `langgraph-core` | Typed graphs, nodes, edges, execution results, retries, async nodes, and parallel execution primitives |
| `langgraph-runtime` | Checkpoint store implementations and runtime-oriented resume behavior |
| `langgraph-memory` | In-memory and file-backed memory store implementations |
| `langgraph-observability` | OpenTelemetry listeners, trace recording, replay, diffing, and trace store implementations |
| `langgraph-spring-boot-starter` | Spring Boot auto-configuration and web integration pieces |
| `langgraph-connectors` | Connector adapters such as OpenAI and Anthropic HTTP clients |

## Requirements

- JDK 21
- Maven 3.9+

GitHub Actions is also configured around JDK 21, so local and CI environments should match.

## Getting Started

Clone the repository and run the full verification build:

```bash
mvn -B -ntp verify
```

If Maven is picking up the wrong JDK locally, verify `mvn -version` and make sure `JAVA_HOME` points to a Java 21 installation.

## Quick Start

The core API uses plain Java records and functions. A graph is assembled explicitly and returns a typed `ExecutionResult`.

```java
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;

record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
    OrderState withValid(boolean value) {
        return new OrderState(id, value, charged, shipped);
    }

    OrderState withCharged(boolean value) {
        return new OrderState(id, valid, value, shipped);
    }

    OrderState withShipped(boolean value) {
        return new OrderState(id, valid, charged, value);
    }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge", (state, ctx) -> state.withCharged(true))
        .node("ship", (state, ctx) -> state.withShipped(true))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)
        .edge("charge", "ship")
        .terminal("ship")
        .build();

ExecutionResult<OrderState> result = graph.run(
        new OrderState("o-1", false, false, false)
);
```

## Retries

Attach a `RetryPolicy` per node, or set a graph default. The executor handles backoff and emits `NodeListener.onRetry`.

```java
RetryPolicy policy = RetryPolicy.builder()
        .maxAttempts(3)
        .backoff(BackoffStrategy.exponential(Duration.ofMillis(100)))
        .build();

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("charge", chargeNode, policy)
        .entry("charge").terminal("charge")
        .build();
```

`Error` and `InterruptedException` always short-circuit retries. Use `ctx.idempotencyKey()` inside the node for your own dedup.

## Replay

Plug in a `TraceRecorder` and replay any past execution step-by-step.

```java
TraceStore store = new InMemoryTraceStore();    // or JsonFileTraceStore / JdbcTraceStore
Graph<OrderState> graph = Graph.<OrderState>builder()
        /* ... */
        .traceRecorder(new RecordingTraceRecorder(store))
        .build();

ExecutionResult<OrderState> r = graph.run(seed);

ExecutionTrace<OrderState> trace =
        (ExecutionTrace<OrderState>) store.load(r.executionId()).orElseThrow();

Replayer<OrderState> replay = Replayer.of(trace);
for (int i = 0; i < replay.stepCount(); i++) {
    TraceStep<OrderState> step = replay.stepAt(i);
    System.out.printf("%d %s : %s -> %s%n",
            step.index(), step.nodeName(), step.before(), step.after());
}

// Re-execute from a chosen step against a (possibly modified) graph
ReplayRunner<OrderState> runner = ReplayRunner.of(trace, graph);
ExecutionResult<OrderState> fork = runner.reRunFrom(1);
// fork.executionId() != r.executionId(); the new trace records forkedFromExecutionId/forkedFromStepIndex
```

## Async + parallel

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        .asyncNode("score", (state, ctx) -> CompletableFuture.supplyAsync(() -> score(state)))
        .parallel("enrich",
                List.of(
                        (s, ctx) -> withCustomerProfile(s),
                        (s, ctx) -> withFraudCheck(s),
                        (s, ctx) -> withInventory(s)
                ),
                Merger.fold(OrderState::merge))
        .entry("score").edge("score", "enrich").terminal("enrich")
        .build();
```

The default executor is virtual-thread-per-task, lazily created per `run` and shut down on completion. First-by-declaration-order failure wins inside `parallel(...)`.

## Observability (OpenTelemetry)

```java
Graph<OrderState> graph = Graph.<OrderState>builder()
        /* ... */
        .listener(OtelNodeListener.usingGlobal())
        .build();
```

One span per node, retries as span events on the same span, errors set `StatusCode.ERROR`. State diffs flow as `state` span events with rendered before/after attributes (renderer is pluggable via `StateRenderer`). Compose multiple listeners with `Listeners.compose(...)`.

## Core Concepts

### Graphs

`Graph<S>` is the main runtime abstraction. You define:

- named nodes
- directed edges
- an entry node
- one or more terminal nodes
- optional retry, checkpoint, trace, listener, memory, and executor behavior

### Nodes

TraceGraph supports several execution styles:

- synchronous nodes via `node(...)`
- asynchronous nodes via `asyncNode(...)`
- parallel branches via `parallel(...)`
- parallel async branches via `parallelAsync(...)`

### Execution Results

Execution returns an `ExecutionResult<S>` that includes:

- `executionId`
- `finalState`
- `path`
- `status`
- `error`

This makes the runtime straightforward to test and inspect.

## Runtime Features

### Retries

Retry policies can be applied per-node or as a graph default. The runtime supports fixed and exponential backoff strategies.

### Checkpointing and Resume

Graphs can be wired to a `CheckpointStore` and resumed later by execution ID:

```java
graph.resume("execution-123");
```

The `langgraph-runtime` module includes an `InMemoryCheckpointStore`, and the extension points are designed for external durable stores.

### Memory

The memory SPI supports scoped key-value persistence for agent-style workflows. Current implementations include:

- `InMemoryMemoryStore`
- `FileMemoryStore`

### Observability and Replay

The observability module includes:

- OpenTelemetry node listeners
- state rendering hooks
- trace recording
- in-memory and JSON file trace stores
- replay and trace diff utilities

This is useful for post-run inspection, debugging, and deterministic replay workflows.

### Spring Boot Integration

The Spring Boot starter auto-configures default beans for:

- `NodeListener`
- `CheckpointStore`
- `TraceRecorder`
- `MemoryStore`

That gives applications a clean way to override infrastructure concerns while keeping graph code simple.

## Build and Test

Useful local commands:

```bash
mvn test
mvn verify
```

The build uses strict compiler settings, including warnings-as-errors via the Maven compiler plugin.

## CI/CD

GitHub Actions is configured for:

- cross-platform CI on Ubuntu and Windows
- pull request security review for dependency changes
- scheduled and main-branch CodeQL analysis
- weekly Dependabot updates for Maven and GitHub Actions
- automated release drafting
- release publishing to GitHub Packages
- GitHub Release creation for tagged versions

Release tags follow the `v*` convention, for example:

```text
v0.1.0
```

## Versioning and Publishing

The project is currently in pre-1.0 development and uses snapshot versions in source control.

Release automation is configured in GitHub Actions. If you are consuming the project before a public package distribution is finalized, the safest option is to build from source and install locally:

```bash
mvn install
```

## Roadmap

Near-term priorities are:

- hardening the graph runtime API
- improving durability and checkpoint integrations
- expanding observability and replay ergonomics
- maturing Spring Boot integration
- stabilizing connector interfaces

## Contributing

Issues and pull requests are welcome.

When contributing:

1. Use JDK 21 and Maven 3.9+.
2. Run `mvn -B -ntp verify` before opening a pull request.
3. Keep changes scoped and add or update tests when behavior changes.
4. Prefer small, reviewable pull requests over broad refactors.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
