# Your First Graph

This page walks you through building and running the simplest possible TraceGraph program — a two-node pipeline that transforms a string.

## Define state

State is any Java type you own. Records are the recommended choice because they are immutable by default.

```java
record PipelineState(String input, String output) {}
```

## Define nodes

A `Node<S>` is a functional interface: it receives the current state and a `Context`, and returns the next state.

```java
Node<PipelineState> normalize = (state, ctx) ->
    new PipelineState(state.input().strip().toLowerCase(), null);

Node<PipelineState> greet = (state, ctx) ->
    new PipelineState(state.input(), "Hello, " + state.input() + "!");
```

## Build the graph

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("normalize", normalize)
    .node("greet", greet)
    .edge("normalize", "greet")
    .entry("normalize")
    .terminal("greet")
    .build();
```

`entry` marks the first node that receives the seed state. `terminal` marks a node after which execution stops.

## Run it

```java
PipelineState initial = new PipelineState("  World  ", null);
ExecutionResult<PipelineState> result = graph.run(initial);

System.out.println(result.finalState().output()); // Hello, world!
System.out.println(result.status());              // COMPLETED
System.out.println(result.executionId());         // UUID
```

`Graph.run` is synchronous and blocks until the graph completes or fails. The returned `ExecutionResult` is an immutable record — safe to pass around freely.

## Key takeaways

- State is a plain Java type; records work best because they are immutable.
- Nodes are pure functions: `(S, Context) -> S`. No side effects on the state object.
- `entry` and `terminal` are required — the builder throws `GraphValidationException` without them.
- `graph.run(seed)` returns an `ExecutionResult<S>` with `finalState`, `status`, and `executionId`.
