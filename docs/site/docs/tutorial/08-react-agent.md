# 08 — ReAct Agent

The ReAct (Reason + Act) pattern alternates between asking the LLM to reason about what action to take and executing that action. `ReActAgent<S>` is a factory that builds a complete `Graph<S>` implementing this loop.

## How the loop works

1. **llm node** — sends the current state (including tool results from prior steps) to the LLM.
2. If the response contains tool calls → route to the **tools node**.
3. **tools node** — executes each requested tool and appends results to state.
4. Route back to **llm node**.
5. When the LLM returns no tool calls → route to **done** (terminal).

## Define state

```java
record AgentState(
    String userQuery,
    List<ChatMessage> history,
    List<ToolResult> lastToolResults,
    String finalAnswer
) {
    static AgentState of(String query) {
        return new AgentState(query, List.of(), List.of(), null);
    }
}
```

## Define tools

```java
Tool calculator = args -> {
    // args is a JSON string per the tool's parametersSchema
    var input = Json.parse(args);
    double result = eval(input.get("expression").asText());
    return String.valueOf(result);
};

ToolDefinition calcDef = new ToolDefinition(
    "calculator",
    "Evaluates a mathematical expression",
    """
    {"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"]}
    """
);
```

## Build the agent graph

```java
LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini")
    .build();

Graph<AgentState> agentGraph = ReActAgent.<AgentState>builder()
    .client(client)
    .tool(calcDef, calculator)
    .requestFactory(state -> LlmRequest.builder()
        .messages(state.history())
        .model("gpt-4o-mini")
        .build())
    .responseFolder((state, response) -> state.withHistory(
        append(state.history(), ChatMessage.assistant(response.content()))
    ))
    .toolResultFolder((state, results) -> state
        .withLastToolResults(results)
        .withHistory(append(state.history(), toMessages(results))))
    .build()
    .buildGraph();
```

## Run it

```java
ExecutionResult<AgentState> result = agentGraph.run(AgentState.of("What is 42 * 17?"));
System.out.println(result.finalState().finalAnswer()); // 714.0
```

The agent will loop as many times as needed until the LLM stops requesting tools.

## Composing the agent as a subgraph

`ReActAgent` produces a regular `Graph<S>`. Use `.subgraph(name, agentGraph)` to embed it inside a larger graph:

```java
Graph<AppState> pipeline = Graph.<AppState>builder()
    .node("prepare",   prepareNode)
    .subgraph("agent", agentGraph)   // inner graph shares <AppState>
    .node("format",    formatNode)
    .edge("prepare", "agent")
    .edge("agent",   "format")
    .entry("prepare")
    .terminal("format")
    .build();
```

## Key takeaways

- `ReActAgent<S>.builder()` produces a complete `Graph<S>` — the llm/tools/done structure is wired for you.
- Supply `requestFactory` and `responseFolder` to control how state maps to `LlmRequest` and back.
- `toolResultFolder` folds executed tool results back into the state before the next LLM call.
- The built graph is a regular `Graph<S>` — embed it as a subgraph or run it standalone.
