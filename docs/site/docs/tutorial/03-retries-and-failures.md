# 03 — Retries & Failures

Production nodes call external services that fail transiently. TraceGraph's retry system is part of graph definition — not runtime configuration — so the policy is reproducible, versionable, and visible in traces.

## RetryPolicy

Create a policy with `RetryPolicy.of(maxAttempts, backoffStrategy)`:

```java
RetryPolicy fixed      = RetryPolicy.of(3, BackoffStrategy.fixed(200));
RetryPolicy exponential = RetryPolicy.of(5, BackoffStrategy.exponential(100, 10_000));
```

`BackoffStrategy.fixed(ms)` waits a constant number of milliseconds between attempts. `BackoffStrategy.exponential(baseMs, maxMs)` doubles the delay each attempt, capped at `maxMs`.

## Attaching a policy to a node

Pass the policy as a third argument to `.node(...)`:

```java
RetryPolicy threeAttempts = RetryPolicy.of(3, BackoffStrategy.exponential(200, 5_000));

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("callApi", callApiNode, threeAttempts)
    .node("process", processNode)
    .edge("callApi", "process")
    .entry("callApi")
    .terminal("process")
    .build();
```

## Default retry policy

Apply a policy to all nodes that don't have their own:

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("callApi", callApiNode, RetryPolicy.of(5, BackoffStrategy.exponential(100, 8_000)))
    .node("process", processNode)
    .defaultRetryPolicy(RetryPolicy.of(2, BackoffStrategy.fixed(500)))
    .entry("callApi")
    .terminal("process")
    .build();
```

Per-node policy beats the default. `process` gets two attempts with 500 ms fixed backoff; `callApi` gets its own five-attempt exponential policy.

## Idempotency on retry

Use `ctx.idempotencyKey()` to avoid double-applying effects. The key is stable across all attempts of the same node within the same execution:

```java
Node<PipelineState> callApiNode = (state, ctx) -> {
    String response = httpClient.post(
        "/enrich",
        state.cleaned(),
        Map.of("Idempotency-Key", ctx.idempotencyKey())
    );
    return state.withResult(response);
};
```

## What retries do not cover

- `Error` and `InterruptedException` always short-circuit the retry loop — they propagate immediately.
- A node that exhausts all attempts surfaces a `NodeExecutionException` which sets `ExecutionResult.status` to `FAILED`.

```java
ExecutionResult<PipelineState> result = graph.run(PipelineState.of("hello"));
if (result.status() == Status.FAILED) {
    result.failureCause().ifPresent(Throwable::printStackTrace);
}
```

## Key takeaways

- `RetryPolicy` is graph-definition time, not runtime config — it's reproducible across environments.
- `BackoffStrategy.fixed` and `.exponential` cover the two common patterns.
- Per-node policy overrides `defaultRetryPolicy`.
- Use `ctx.idempotencyKey()` to make external calls idempotent across retry attempts.
- `Error` and `InterruptedException` bypass retries entirely.
