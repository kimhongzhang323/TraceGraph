# TraceGraph ReAct Agent Example

Demonstrates the `ReActAgent` pattern: an LLM reasons, calls tools, observes results, and loops until done. Uses a `MockLlmClient` — no API key needed.

## Run

```bash
mvn -f examples/react-agent/pom.xml exec:java
```

Expected output:
```
Answer: The answer is 42
```

## What it demonstrates

- `ReActAgent` wiring: `client`, `tool`, `requestFactory`, `responseFolder`, `toolResultFolder`
- Custom `LlmClient` lambda simulating a two-turn conversation with tool use
- A simple `calculator` tool that evaluates math expressions
