# HITL (Human-in-the-Loop) Approval

This cookbook demonstrates how to build a graph that pauses execution to wait for human approval before proceeding to sensitive steps.

## Overview

In many workflows, you cannot allow an AI agent to execute critical actions (like sending an email, deleting a database record, or transferring money) without explicit human confirmation.

TraceGraph handles this using checkpoints and interrupts.

1. The graph executes up to a predefined breakpoint.
2. The execution is suspended, and the state is persisted to memory.
3. A human reviews the state (e.g., via a UI or an API call).
4. The execution is resumed with an updated state or an approval flag.

See the runnable example at [`examples/hitl-approval/`](https://github.com/kimho/TraceGraph/tree/main/examples/hitl-approval).
