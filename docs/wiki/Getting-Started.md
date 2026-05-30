# Getting Started

This page takes you from zero to a running graph and points you at the next steps.

## Requirements

- **JDK 21** (records, pattern matching, virtual threads)
- **Maven 3.9+**

If Maven is picking up the wrong JDK, check `mvn -version` and make sure `JAVA_HOME` points at a Java 21 install. GitHub Actions CI is also built around JDK 21, so local and CI environments match.

## Installation

Pick the smallest module set that matches your use case. The Maven `groupId` is `site.tracegraph`; Java package names are `io.tracegraph.*` (independent of the groupId).

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>tracegraph-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

Add supporting modules as needed:

| Module | Add it when… |
|---|---|
| `tracegraph-runtime` | executions must survive process boundaries or resume from checkpoints |
| `tracegraph-memory` | nodes need scoped cross-run memory |
| `tracegraph-observability` | you need trace replay, debugging artifacts, or OpenTelemetry spans |
| `tracegraph-connectors` | graph nodes need LLM, prompt, structured output, or MCP helpers |
| `tracegraph-rag` | you want retrieval and reranking utilities |
| `tracegraph-spring-boot-starter` | the runtime should plug into a Spring Boot app with minimal wiring |

See **[[Modules]]** for the full list (including `tracegraph-eval`, `tracegraph-a2a`, `tracegraph-bench`).

### Building from source

If you are consuming the repository before a stable public release:

```bash
mvn -B -ntp install      # install all artifacts to the local repo
```

## Your first graph

The core API uses plain Java records and functions. A graph is assembled explicitly and returns a typed `ExecutionResult`.

```java
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;

record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
    OrderState withValid(boolean value)   { return new OrderState(id, value, charged, shipped); }
    OrderState withCharged(boolean value) { return new OrderState(id, valid, value, shipped); }
    OrderState withShipped(boolean value) { return new OrderState(id, valid, charged, value); }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge",   (state, ctx) -> state.withCharged(true))
        .node("ship",     (state, ctx) -> state.withShipped(true))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)   // conditional edge
        .edge("charge", "ship")                          // unconditional edge
        .terminal("ship")
        .build();

ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));
```

`ExecutionResult<S>` exposes `executionId`, `finalState`, `path`, `status`, and `error`. See **[[Core Concepts]]** and **[[Execution Model]]**.

### Adding durability and observability

The same graph can be extended with retries, trace recording, and OpenTelemetry:

```java
Graph<OrderState> durableGraph = Graph.<OrderState>builder()
        .node("validate", (state, ctx) -> state.withValid(true))
        .node("charge",   (state, ctx) -> state.withCharged(true),
                RetryPolicy.fixed(3, Duration.ofMillis(100)))
        .entry("validate")
        .edge("validate", "charge", OrderState::valid)
        .terminal("charge")
        .traceRecorder(new RecordingTraceRecorder(new InMemoryTraceStore()))
        .listener(OtelNodeListener.usingGlobal())
        .build();
```

## Recommended adoption sequence

1. Build the repo with `mvn -B -ntp verify`.
2. Run `examples/quickstart` for a small pure-Java graph.
3. Move to `examples/spring-boot-app` if you want HTTP endpoints and auto-configuration.
4. Add observability, replay, memory, or connectors **module-by-module** instead of all at once.

For many teams the best path is **`core` first, `observability` second**, then only the storage and connector modules you actually need.

## Runnable examples

The repository ships small runnable examples:

```bash
mvn -f examples/quickstart/pom.xml exec:java
mvn -f examples/spring-boot-app/pom.xml spring-boot:run
mvn -f examples/rag-agent/pom.xml exec:java
mvn -f examples/react-agent/pom.xml exec:java
mvn -f examples/hitl-approval/pom.xml exec:java
```

| Example | Shows |
|---|---|
| `examples/quickstart` | the smallest graph setup |
| `examples/spring-boot-app` | starter-based HTTP integration |
| `examples/rag-agent` | retrieval-augmented flows |
| `examples/react-agent` | ReAct-style tool use |
| `examples/hitl-approval` | human-in-the-loop approval |

## Build and test

```bash
mvn test                       # run all tests
mvn -pl tracegraph-core test    # test one module
mvn verify                     # full verification build
mvn -B install -DskipTests     # install artifacts to local repo
mvn clean                      # clear target/
```

The build uses **strict compiler settings** (`-Xlint:all -Werror`) — warnings break the build.

---

**Next:** **[[Core Concepts]]** → **[[Execution Model]]** → **[[Runtime Features]]**
