# 01 — Nodes & Edges

Nodes and edges are the two primitives that every TraceGraph program is built from. This tutorial introduces both and shows how they combine into a runnable graph.

## State record

All tutorials share a common state record that evolves as features are added.

```java
record PipelineState(String input, String cleaned, String result) {
    static PipelineState of(String input) {
        return new PipelineState(input, null, null);
    }
}
```

## Nodes

A `Node<S>` is a `@FunctionalInterface` with signature `(S state, Context ctx) -> S`. The node receives the current state, performs its work, and returns the **next** state. It must never mutate the state object it receives.

```java
Node<PipelineState> clean = (state, ctx) ->
    new PipelineState(state.input(), state.input().strip().toLowerCase(), null);

Node<PipelineState> shout = (state, ctx) ->
    new PipelineState(state.input(), state.cleaned(), state.cleaned().toUpperCase() + "!");
```

## Unconditional edges

`.edge(from, to)` creates a directed edge that is always followed after the source node completes.

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("clean", clean)
    .node("shout", shout)
    .edge("clean", "shout")
    .entry("clean")
    .terminal("shout")
    .build();
```

## Conditional edges

`.edge(from, to, predicate)` adds a guarded edge. The first edge whose predicate returns `true` (in declaration order) is followed.

```java
Node<PipelineState> warn = (state, ctx) ->
    new PipelineState(state.input(), state.cleaned(), "[WARNING] empty input");

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("clean", clean)
    .node("shout", shout)
    .node("warn", warn)
    .edge("clean", "warn",  s -> s.cleaned().isEmpty())
    .edge("clean", "shout", s -> !s.cleaned().isEmpty())
    .entry("clean")
    .terminal("shout")
    .terminal("warn")
    .build();
```

Edge predicates must be **pure functions of state** — they are re-evaluated on resume after a checkpoint, so side effects inside a predicate will cause inconsistent routing.

## Running

```java
ExecutionResult<PipelineState> result = graph.run(PipelineState.of("  Hello  "));
System.out.println(result.finalState().result()); // HELLO!
```

## Key takeaways

- `Node<S>` is `(S, Context) -> S` — return the new state, never mutate the old one.
- Unconditional edges with `.edge(from, to)` always fire.
- Conditional edges with `.edge(from, to, predicate)` are evaluated in declaration order; first match wins.
- Edge predicates must be pure — no side effects, no I/O.
