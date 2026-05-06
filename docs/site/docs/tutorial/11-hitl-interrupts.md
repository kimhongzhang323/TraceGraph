# 11 — HITL Interrupts

Human-in-the-Loop (HITL) pauses let an operator inspect or approve a graph's state before execution continues. TraceGraph implements this as a first-class interrupt mechanism — no polling, no sleep loops.

## interruptBefore and interruptAfter

Declare interrupt points at graph build time:

```java
Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("draft",   draftNode)
    .node("review",  reviewNode)
    .node("publish", publishNode)
    .edge("draft",   "review")
    .edge("review",  "publish")
    .entry("draft")
    .terminal("publish")
    .checkpointStore(checkpointStore)
    .interruptBefore("publish")   // pause before publishing; operator must approve
    .build();
```

`interruptBefore("publish")` pauses the executor just before `publish` would run and writes a checkpoint. `interruptAfter("review")` runs `review` to completion, writes the checkpoint, then pauses.

## Running to an interrupt

```java
ExecutionResult<ApprovalState> result = graph.run(ApprovalState.of("Draft content..."));
System.out.println(result.status());      // INTERRUPTED
System.out.println(result.executionId()); // save this to resume later
```

When the executor hits an `interruptBefore` point it sets `Status.INTERRUPTED` and returns. The state at that moment is saved in the checkpoint store.

## Inspecting state before resuming

Load the checkpoint (or trace) to see the pending state:

```java
ApprovalState pending = checkpointStore.load(executionId)
    .map(Checkpoint::state)
    .orElseThrow();

System.out.println(pending.draft()); // operator reviews this
```

## Resuming after approval

```java
// Operator approved — resume
ExecutionResult<ApprovalState> completed = graph.resume(executionId);
System.out.println(completed.status()); // COMPLETED
```

`graph.resume(id)` continues from the checkpoint. The `publish` node runs normally.

## Spring Boot REST endpoint

When using the Spring Boot starter, the interrupt/resume flow has REST endpoints out of the box:

```
# Check whether a run is interrupted
GET /tracegraph/traces/{id}        → { "status": "INTERRUPTED", ... }

# Resume
POST /tracegraph/traces/{id}/resume
→ 200 { "status": "COMPLETED" }   (or 404 if unknown, 409 if not INTERRUPTED)
```

## Handling modifications before resume

If the operator wants to modify state before continuing, load, modify, and re-save the checkpoint before calling `resume`:

```java
Checkpoint<ApprovalState> cp = checkpointStore.load(executionId).orElseThrow();
ApprovalState approved = cp.state().withApproverNote("LGTM — proceed.");
checkpointStore.save(cp.withState(approved));

graph.resume(executionId);
```

## Key takeaways

- `interruptBefore(name)` pauses before the named node; `interruptAfter(name)` pauses after it.
- Interrupted runs return `Status.INTERRUPTED` and write a checkpoint — they do not throw.
- `graph.resume(id)` picks up from the checkpoint; the interrupted node runs normally on resume.
- Per-branch interrupts inside `parallel(...)` are not supported.
- The Spring Boot starter exposes `POST /tracegraph/traces/{id}/resume` for REST-based approval flows.
