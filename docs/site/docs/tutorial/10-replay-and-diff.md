# 10 — Replay & Diff

Replay lets you re-execute a saved trace from any step — essential for debugging, prompt iteration, and regression testing. Diff compares two traces structurally.

## Recording a trace

Wire a `TraceRecorder` and `TraceStore` at graph build time:

```java
InMemoryTraceStore<RagState> traceStore = new InMemoryTraceStore<>();
TraceRecorder<RagState> recorder = new RecordingTraceRecorder<>(traceStore);

Graph<RagState> graph = Graph.<RagState>builder()
    // ... nodes and edges ...
    .traceRecorder(recorder)
    .build();

ExecutionResult<RagState> result = graph.run(RagState.of("What is the capital of France?"));
String executionId = result.executionId();
```

## Inspecting the trace

```java
ExecutionTrace<RagState> trace = traceStore.load(executionId).orElseThrow();

for (TraceStep<RagState> step : trace.steps()) {
    System.out.printf("%-12s  attempts=%d  before=%s%n",
        step.nodeName(), step.attempts(), step.before());
}
```

Each `TraceStep` records `nodeName`, `before` state, `after` state, `attempts` count, and per-step `usage` (prompt/completion tokens for LLM nodes).

## Replaying from a step

`ReplayRunner` re-executes from a chosen step index against the same (or a modified) graph:

```java
Graph<RagState> improvedGraph = // ... graph with a better prompt ...

ReplayRunner<RagState> runner = ReplayRunner.of(trace, improvedGraph);
ExecutionResult<RagState> forked = runner.reRunFrom(1);  // re-run from step index 1

System.out.println(forked.executionId());          // new UUID
System.out.println(forked.forkedFromExecutionId()); // original UUID
System.out.println(forked.forkedFromStepIndex());   // 1
```

Pass `stepIndex = -1` to replay from the entry node using the original seed state. The new `ExecutionTrace` carries `forkedFromExecutionId` and `forkedFromStepIndex` to record lineage.

## Supplying an alternative seed

Override the seed state for the replay by passing a second argument:

```java
RagState alternativeSeed = trace.steps().get(1).before().withQuery("Different question?");
ExecutionResult<RagState> forked = runner.reRunFrom(1, alternativeSeed);
```

## Comparing two traces

`TraceDiff.between(left, right)` walks two traces step-by-step and finds the longest common prefix:

```java
ExecutionTrace<RagState> original = traceStore.load(executionId).orElseThrow();
ExecutionTrace<RagState> forked   = traceStore.load(forked.executionId()).orElseThrow();

TraceDiff<RagState> diff = TraceDiff.between(original, forked);

System.out.println("Diverged at step: " + diff.divergenceIndex());
System.out.println("Same final state: " + diff.sameFinalState());
System.out.println("Identical:        " + diff.identical());
```

`diff.leftRemainder()` and `diff.rightRemainder()` contain the steps after divergence in each trace.

## Persistent traces with JsonFileTraceStore

```java
TraceStore<RagState> fileStore = JsonFileTraceStore.of(
    Path.of("/var/tracegraph/traces"),
    RagState.class
);
```

Files are written atomically (`*.tmp` + `ATOMIC_MOVE`). Throwable round-trip is lossy — only class name and message are preserved.

## Key takeaways

- `RecordingTraceRecorder` + `InMemoryTraceStore` (or `JsonFileTraceStore`) captures full step-by-step history.
- `ReplayRunner.of(trace, graph).reRunFrom(stepIndex)` re-executes from any step with a new executionId.
- The forked trace carries `forkedFromExecutionId` + `forkedFromStepIndex` for lineage tracking.
- `TraceDiff.between(a, b)` finds the first divergence point and compares final state.
