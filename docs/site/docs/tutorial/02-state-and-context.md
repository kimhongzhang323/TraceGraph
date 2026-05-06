# 02 — State & Context

Every node receives two arguments: the current state and a `Context` object. This tutorial explains what `Context` provides and how to design state for real workloads.

## Evolving the state record

Real pipelines accumulate data as they progress. Add fields for the execution metadata you need:

```java
record PipelineState(
    String input,
    String cleaned,
    String result,
    List<String> log
) {
    static PipelineState of(String input) {
        return new PipelineState(input, null, null, List.of());
    }

    PipelineState withLog(String entry) {
        var next = new ArrayList<>(log);
        next.add(entry);
        return new PipelineState(input, cleaned, result, List.copyOf(next));
    }
}
```

## Using Context

`Context` is passed to every node and carries execution-scoped metadata.

```java
Node<PipelineState> clean = (state, ctx) -> {
    String cleaned = state.input().strip().toLowerCase();
    String logEntry = "[%s] cleaned".formatted(ctx.executionId());
    return new PipelineState(state.input(), cleaned, null, state.log())
        .withLog(logEntry);
};
```

### `ctx.executionId()`

A stable UUID for the current run. Use it when logging or correlating traces across services.

### `ctx.idempotencyKey()`

A deterministic key derived from `executionId` + node name + attempt number. Pass it to upstream HTTP or JDBC calls to make retries safe without double-applying effects.

```java
Node<PipelineState> callApi = (state, ctx) -> {
    String result = externalService.call(state.cleaned(), ctx.idempotencyKey());
    return new PipelineState(state.input(), state.cleaned(), result, state.log());
};
```

### `ctx.memory()`

Access to the `MemoryStore` for cross-execution persistence. Covered in depth in [Tutorial 06](06-memory.md).

### `ctx.reportUsage(promptTokens, completionTokens)`

Used by LLM nodes to report token consumption to listeners. Covered in [Tutorial 07](07-llm-and-tools.md).

## State composition vs. generic result types

TraceGraph uses a single type parameter `<S>`. Instead of `Node<S, R>`, fold sub-results back into the state:

```java
// Good — sub-result is a field on the state record
record PipelineState(String input, String cleaned, String result, ...) {}

// Avoid — two type parameters break builder inference and complicate resumption
// Node<S, R>  ← not how TraceGraph works
```

## Key takeaways

- `ctx.executionId()` gives a stable UUID per run; use it for logging and correlation.
- `ctx.idempotencyKey()` is attempt-scoped; pass it to external calls to enable safe retries.
- State is a value type — nodes return a new state, never mutate the received one.
- Fold sub-results into fields on the state record rather than adding type parameters.
