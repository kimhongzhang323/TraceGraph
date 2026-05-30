# 多智能体模式

`ReActAgent<S>` 是单智能体原语（见 **[[LLM 连接器|zh-LLM-Connectors]]**）。`0.3.0` 增加了四种把多个 ReAct 智能体组合成 `Graph<S>` 的方式，以及一个为它们提供收敛保证的终止监听器。它们都在 `tracegraph-connectors`。

> 🌐 English: **[[Multi-Agent Patterns]]**

| 模式 | 类型 | 形态 |
|---|---|---|
| 点对点委派 | `HandoffNode<S>` | 一个智能体按名把控制权交给另一个 |
| 角色/工具隔离 | `AgentProfile<S>` | 每智能体的系统提示词 + 工具集 |
| N 智能体轮转 | `GroupChatAgent<S>` | 轮询或 LLM 选定发言者 |
| 并行共识 | `VotingNode<S>` | 扇出候选，用 `Tally` 聚合 |

## HandoffNode —— 点对点委派

一个智能体通过从状态读取目标名，把控制权直接交给另一个——**循环中无中央 supervisor**。

```java
Graph<ChatState> graph = HandoffNode.<ChatState>builder()
        .client(llmClient)
        .requestFactory(state -> LlmRequest.of(state.messages()))
        .responseFolder((state, resp) -> state.withMessage(resp.content()))
        .handoffSelector(state -> state.routeTo())
        .target("alice", aliceAgentGraph)
        .target("bob", bobAgentGraph)
        .build()
        .buildGraph();
```

选择器语义：

- `"continue"`（保留值）——落入下一个声明的目标。
- `null` 或**未知**名称——在 `"done"` 终止。

## AgentProfile —— 角色与工具隔离

`AgentProfile<S>` 是 `(name, systemPrompt, List<Tool>, List<ToolDefinition>, memoryScope)` 记录，覆盖 `ReActAgent.Builder` 上的角色提示词与工具。调用 `.profile(...)` 会**替换**先前的 `tool(...)` 注册，因此同一 `LlmClient` 上的两个智能体看不到彼此的工具。

```java
AgentProfile<S> researcher = new AgentProfile<>(
        "researcher", "You are a research analyst.",
        List.of(searchTool, fetchTool), List.of(searchDef, fetchDef),
        Function.identity());

Graph<S> graph = ReActAgent.<S>builder()
        .client(llmClient).profile(researcher)
        .requestFactory(...).responseFolder(...).toolResultFolder(...)
        .build().buildGraph();
```

`memoryScope` 接入 `MemoryStore`（见 **[[记忆|zh-Memory]]**），使每个智能体读写自己的作用域。

## GroupChatAgent —— 轮询或 LLM 选定发言者

用 `SpeakerSelector<S>` 策略轮转 N 个具名 `ReActAgent`，并在用户提供的 `terminationPredicate` 上停止。

```java
Graph<S> chat = GroupChatAgent.<S>builder()
        .agent("alice", aliceGraph).agent("bob", bobGraph).agent("carol", carolGraph)
        .speakerSelector(SpeakerSelector.roundRobin())   // 或 SpeakerSelector.llm(...)
        .terminationPredicate(state -> state.rounds() >= 4)
        .build().buildGraph();
```

- `SpeakerSelector.roundRobin()`——按声明顺序循环。
- `SpeakerSelector.llm(...)`——让 LLM 挑选下一个发言者。

## VotingNode —— 并行共识

用 `parallel(...)`（见 **[[运行时特性|zh-Runtime-Features]]**）在候选 ReAct 子图上扇出，再用 `Tally` 聚合它们的状态。

```java
Node<S> vote = VotingNode.<S>builder()
        .candidate("alice", aliceGraph).candidate("bob", bobGraph).candidate("carol", carolGraph)
        .tally(Tally.majority(State::answer))   // 或 Tally.firstNonNull(State::answer)
        .build();
```

内置聚合：

- `Tally.majority(Function<S, String>)`——最常见答案胜出。
- `Tally.firstNonNull(Function<S, String>)`——首个非空答案的候选。

## 终止保证

引入 `tracegraph-observability` 的 `TerminationListener<S>`，为**所有四种**模式提供收敛保证：

```java
TerminationListener<S> term = TerminationListener.<S>builder()
        .maxTurns(8).afterNode("done").stateMatches(state -> state.isConverged())
        .build();
```

谓词触发时，执行器给出干净的 **`Status.TERMINATED`**（监听器抛 `TerminationSignalException`，由执行器捕获）。

## 智能体到智能体（A2A）模块

若智能体需要**跨进程或网络互相发消息**——而非在一个 `Graph<S>` 内组合——见 `tracegraph-a2a`：

- `Agent<S>`、`AgentBus` SPI、`InMemoryAgentBus`（虚拟线程派发）。
- `AgentMessage` 记录，带 `of()` / `reply()` 工厂；`AgentTimeoutException`。
- HTTP 传输（`A2AHttpClient`、`A2AMessage`、`A2AHttpException`），与 Google A2A JSON 线协议兼容。
- Spring Boot：`A2AAutoConfiguration` 注册 `InMemoryAgentBus`；`A2AController` 暴露 `POST /a2a/messages`。

```java
A2AMessage request = A2AMessage.builder()
    .from("manager_agent").to("worker_agent")
    .payload("{\"task\": \"summarize_logs\"}")
    .build();
A2AMessage response = dispatcher.sendAndWait(request);
```

---

**相关：** **[[LLM 连接器|zh-LLM-Connectors]]** · **[[记忆|zh-Memory]]** · **[[可观测性与重放|zh-Observability-and-Replay]]** · **[[评估|zh-Evaluation]]**
