# ReAct Agent

This cookbook demonstrates how to build a ReAct (Reasoning and Acting) style agent.

## Overview

A ReAct agent uses an LLM to interleave reasoning and tool use. It will:
1. Receive a task.
2. Decide if it needs to use a tool to gather information or perform an action.
3. If yes, it executes the tool, observes the result, and loops back to step 2.
4. If no further tools are needed, it generates the final answer.

This pattern is natively supported by TraceGraph's conditional routing and tool execution nodes.

See the runnable example at [`examples/react-agent/`](https://github.com/kimho/TraceGraph/tree/main/examples/react-agent).
