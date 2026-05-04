# TraceGraph Quickstart

The simplest possible TraceGraph example — a two-node graph that greets and shouts.

## Run

```bash
mvn -f examples/quickstart/pom.xml exec:java
```

Expected output:
```
Final greeting: Hello, TraceGraph! (SHOUTED: HELLO, TRACEGRAPH!)
```

## What it demonstrates

- Defining a typed state record
- Building a graph with `Graph.builder()`
- Adding nodes and wiring edges
- Running with `graph.run(initialState)` and reading `ExecutionResult.finalState()`
