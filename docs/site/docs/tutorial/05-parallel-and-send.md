# 05 — Parallel & Send

TraceGraph supports two forms of concurrent execution: static `parallel` branches declared at graph-build time, and dynamic `sendAll` fan-out resolved at runtime inside a routing node.

## Static parallel branches

`.parallel(name, branches, merger)` runs a fixed list of `Node<S>` branches concurrently. All branches receive the same input state; their results are merged in declaration order by the `merger` function.

```java
record EnrichState(String input, String geoResult, String sentimentResult, String combined) {}

Node<EnrichState> geoNode       = (s, ctx) -> s.withGeoResult(geoApi.lookup(s.input()));
Node<EnrichState> sentimentNode = (s, ctx) -> s.withSentimentResult(sentimentApi.analyze(s.input()));

Graph<EnrichState> graph = Graph.<EnrichState>builder()
    .parallel(
        "enrich",
        List.of(geoNode, sentimentNode),
        (a, b) -> new EnrichState(a.input(), a.geoResult(), b.sentimentResult(), null)
    )
    .node("combine", (s, ctx) -> s.withCombined(s.geoResult() + " | " + s.sentimentResult()))
    .edge("enrich", "combine")
    .entry("enrich")
    .terminal("combine")
    .build();
```

Branches run on a virtual-thread executor. They are anonymous — they don't appear in trace steps or fire listener events. If any branch throws, the first failure (in declaration order) propagates.

## Custom executor

By default, a virtual-thread-per-task executor is created per `run` call and shut down on completion. Supply your own to control thread pool lifecycle:

```java
ExecutorService sharedPool = Executors.newVirtualThreadPerTaskExecutor();

Graph<EnrichState> graph = Graph.<EnrichState>builder()
    // ... nodes ...
    .executor(sharedPool)   // NOT shut down by the graph
    .build();
```

User-supplied executors are never shut down by the graph.

## Dynamic fan-out with sendAll

When the number of parallel targets is not known at build time, use `NodeResult.sendAll(...)` inside a `RoutingNode`:

```java
record BatchState(List<String> items, Map<String, String> results) {}

Graph<BatchState> graph = Graph.<BatchState>builder()
    .routingNode("dispatch", (state, ctx) -> {
        List<Send<BatchState>> sends = state.items().stream()
            .map(item -> new Send<>("process", state.withSingleItem(item)))
            .toList();
        return NodeResult.sendAll(sends, BatchState::merge, state);
    })
    .node("process", (s, ctx) -> s.withResult(s.currentItem(), process(s.currentItem())))
    .edge("process", "done")
    .entry("dispatch")
    .terminal("done")
    .build();
```

`sendAll` expands identically to `parallel` at runtime — branches run concurrently on the configured executor.

## Key takeaways

- `parallel(name, branches, merger)` is declared at build time; all branches receive the same input state.
- Branches are anonymous — no names, no trace steps, no listener events.
- First-by-declaration-order failure wins when multiple branches throw.
- `NodeResult.sendAll(...)` inside a routing node enables runtime-determined fan-out.
- User-supplied executors via `.executor(...)` are not shut down by the graph.
