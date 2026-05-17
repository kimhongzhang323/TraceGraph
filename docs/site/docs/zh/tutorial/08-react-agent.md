---
title: ReAct 代理
---

# 08 — ReAct 代理

ReAct（Reason + Act，推理 + 行动）模式交替进行两个步骤：让 LLM 推理下一步应采取什么行动，然后执行该行动。`ReActAgent<S>` 是一个工厂，可构建实现此循环的完整 `Graph<S>`。

## 循环工作原理

1. **llm 节点** — 将当前状态（包括前几步的工具调用结果）发送给 LLM。
2. 如果响应包含工具调用 → 路由到 **tools 节点**。
3. **tools 节点** — 执行每个请求的工具并将结果追加到状态中。
4. 路由回 **llm 节点**。
5. 当 LLM 不再返回工具调用时 → 路由到 **done**（终止节点）。

## 定义状态

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

## 定义工具

```java
Tool calculator = args -> {
    // args 是符合工具 parametersSchema 的 JSON 字符串
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

## 构建代理图

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

## 运行代理

```java
ExecutionResult<AgentState> result = agentGraph.run(AgentState.of("What is 42 * 17?"));
System.out.println(result.finalState().finalAnswer()); // 714.0
```

代理会持续循环，直到 LLM 停止请求工具为止。

## 将代理作为子图组合

`ReActAgent` 生成一个普通的 `Graph<S>`。使用 `.subgraph(name, agentGraph)` 可将其嵌入到更大的图中：

```java
Graph<AppState> pipeline = Graph.<AppState>builder()
    .node("prepare",   prepareNode)
    .subgraph("agent", agentGraph)   // 内部图共享 <AppState>
    .node("format",    formatNode)
    .edge("prepare", "agent")
    .edge("agent",   "format")
    .entry("prepare")
    .terminal("format")
    .build();
```

## 要点总结

- `ReActAgent<S>.builder()` 生成完整的 `Graph<S>` — llm/tools/done 结构已为您连接好。
- 提供 `requestFactory` 和 `responseFolder` 来控制状态与 `LlmRequest` 之间的映射关系。
- `toolResultFolder` 在下一次 LLM 调用之前，将已执行的工具结果折叠回状态中。
- 构建出的图是普通的 `Graph<S>` — 可作为子图嵌入，也可独立运行。
