# TraceGraph HITL Approval Example

Demonstrates the human-in-the-loop (HITL) interrupt pattern. The graph pauses before a critical node, allowing a human (or system) to review and approve before continuing.

## Run

```bash
mvn -f examples/hitl-approval/pom.xml exec:java
```

Expected output:
```
Status: INTERRUPTED
State at interrupt: ApprovalState[action=deploy-to-prod, status=pending approval for: deploy-to-prod]
Path so far: [prepare]
Simulating human approval...
Resumed status: COMPLETED
Final state: ApprovalState[action=deploy-to-prod, status=executed: deploy-to-prod]
```

## What it demonstrates

- `Builder.interruptBefore("execute")` — pauses graph before the named node
- `InMemoryCheckpointStore` — persists execution state across the interrupt
- `graph.run()` returns `Status.INTERRUPTED` — execution paused, not failed
- `graph.resume(executionId)` — continues from where it left off
- State is fully preserved across the interrupt boundary
