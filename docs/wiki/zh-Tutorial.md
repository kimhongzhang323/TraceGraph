# 教程

循序渐进的 TraceGraph 教程。每一部分在共享示例上增加一项能力。首次请从头读到尾；之后可作参考。

> 🌐 English: **[[Tutorial]]**

**目录**

1. [节点与边](#1--节点与边)
2. [状态与上下文](#2--状态与上下文)
3. [重试与失败](#3--重试与失败)
4. [检查点与恢复](#4--检查点与恢复)
5. [并行与 Send](#5--并行与-send)
6. [记忆](#6--记忆)
7. [LLM 与工具](#7--llm-与工具)
8. [ReAct 智能体](#8--react-智能体)
9. [RAG 流水线](#9--rag-流水线)
10. [重放与差异](#10--重放与差异)
11. [HITL 中断](#11--hitl-中断)

---

## 1 — 节点与边

节点与边是每个 TraceGraph 程序的两个原语。教程共享一个随功能增长的状态 record：

```java
record PipelineState(String input, String cleaned, String result) {
    static PipelineState of(String input) { return new PipelineState(input, null, null); }
}
```

`Node<S>` 是签名为 `(S state, Context ctx) -> S` 的 `@FunctionalInterface`。它接收当前状态、做工作、返回**下一个**状态——绝不就地修改。

```java
Node<PipelineState> clean = (state, ctx) ->
    new PipelineState(state.input(), state.input().strip().toLowerCase(), null);
```

**无条件边**（`.edge(from, to)`）总在源节点完成后触发。**条件边**（`.edge(from, to, predicate)`）按声明顺序求值；首个返回 `true` 的胜出。

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("clean", clean)
    .node("shout", shout)
    .node("warn", (s, ctx) -> new PipelineState(s.input(), s.cleaned(), "[WARNING] empty input"))
    .edge("clean", "warn",  s -> s.cleaned().isEmpty())
    .edge("clean", "shout", s -> !s.cleaned().isEmpty())
    .entry("clean").terminal("shout").terminal("warn")
    .build();

ExecutionResult<PipelineState> result = graph.run(PipelineState.of("  Hello  "));
System.out.println(result.finalState().result()); // HELLO!
```

**要点：** `Node<S>` 是 `(S, Context) -> S`；返回新状态。条件边按声明顺序首个匹配胜出。**边谓词必须是纯函数**（恢复时会被重新求值）。

---

## 2 — 状态与上下文

每个节点接收状态与一个 `Context`。真实流水线会积累数据，因此扩展 record（加 `log` 等字段，配 `withLog(...)` 拷贝方法）。`Context` 携带执行级元数据：

| 方法 | 用途 |
|---|---|
| `ctx.executionId()` | 每次运行的稳定 UUID——日志/跨服务关联 |
| `ctx.idempotencyKey()` | 尝试级键——传给 HTTP/JDBC 使重试不重复生效 |
| `ctx.memory()` | `MemoryStore`（见第 6 部分） |
| `ctx.reportUsage(prompt, completion)` | LLM 节点上报 token 用量（见第 7 部分） |

**状态组合优于泛型结果。** TraceGraph 用单一类型参数 `<S>`。子结果折进状态字段，而非用 `Node<S, R>`——两个类型参数会破坏构建器推断并使恢复复杂化。

---

## 3 — 重试与失败

重试是**图定义**，而非运行时配置——策略可复现、可版本化、在追踪中可见。

```java
RetryPolicy fixed       = RetryPolicy.of(3, BackoffStrategy.fixed(200));
RetryPolicy exponential = RetryPolicy.of(5, BackoffStrategy.exponential(100, 10_000));
```

逐节点附加（第三个参数）或设图默认——**逐节点胜过默认**。用 `ctx.idempotencyKey()` 使外部调用跨尝试安全。`Error` 与 `InterruptedException` **总是短路**重试。耗尽尝试的节点抛 `NodeExecutionException` 并把 `ExecutionResult.status` 置为 `FAILED`。

---

## 4 — 检查点与恢复

长流程需在重启后存活。节点成功退出后、求值出边**之前**，TraceGraph 写入检查点（`executionId`、`lastCompletedNode`、状态）。恢复时加载检查点、重新求值 `lastCompletedNode` 的出边、继续。

```java
CheckpointStore<PipelineState> store = new InMemoryCheckpointStore<>();   // 开发
// 生产：
JdbcCheckpointStore<PipelineState> jdbc = new JdbcCheckpointStore<>(dataSource, PipelineState.class);
jdbc.initSchema();
```

```java
String executionId = UUID.randomUUID().toString();
graph.run(PipelineState.of("hello"), executionId);   // 中途崩溃
ExecutionResult<PipelineState> resumed = graph.resume(executionId);  // COMPLETED
```

**至少一次：** 节点中途崩溃会从第 1 次尝试重跑。用 `ctx.idempotencyKey()` 使节点幂等。**边谓词必须是纯函数**（恢复时重新求值）。

---

## 5 — 并行与 Send

两种并发：构建期的静态 `parallel` 分支，运行期的动态 `sendAll` 扇出。

```java
.parallel("enrich",
        List.of(geoNode, sentimentNode),
        (a, b) -> new EnrichState(a.input(), a.geoResult(), b.sentimentResult(), null))
```

所有分支接收**相同输入状态**；结果按声明顺序合并。分支在虚拟线程上运行、**匿名**（无追踪步骤、无监听器事件），**按声明顺序第一个失败胜出**。用户 `.executor(...)` 不会被图关闭。

目标在运行时决定时，在 `RoutingNode` 内用 `NodeResult.sendAll(...)`，其展开与 `parallel` 一致。

---

## 6 — 记忆

工作记忆是状态对象（单次执行）。`MemoryStore` SPI 提供**跨执行**持久化，经 `ctx.memory()` 访问：

```java
ctx.memory().put("user:42", "preferences", Map.of("lang", "en"));
Object prefs = ctx.memory().get("user:42", "preferences");
```

第一个参数是**作用域**，第二个是键。用 `.memoryStore(store)` 接入；未接入则 `ctx.memory()` 是丢弃写入的 no-op。生产实现：`FileMemoryStore.of(path)` 与 `new JdbcMemoryStore(dataSource)`（调 `initSchema()`）。两者经 Jackson 多态序列化往返异构值，并拒绝含 `/`、`\`、`..` 的 scope/key。见 **[[记忆|zh-Memory]]**。

---

## 7 — LLM 与工具

连接器模块给出与厂商无关的 `LlmClient` 与 `ChatNode<S>` 适配器。`ChatNode` 经两个函数桥接——`requestBuilder`（状态 → 请求）与 `responseFolder`（状态 + 响应 → 状态）：

```java
Node<ChatState> chatNode = ChatNode.<ChatState>builder()
    .client(client)
    .requestBuilder(state -> LlmRequest.builder()
        .message(ChatMessage.user(state.userMessage())).model("gpt-4o-mini").maxTokens(512).build())
    .responseFolder((state, response) -> new ChatState(
        state.userMessage(), response.content(),
        response.usage().promptTokens(), response.usage().completionTokens()))
    .build();
```

`ChatNode` 在每次响应后自动调用 `ctx.reportUsage(...)`，使 token 用量出现在追踪步骤与 OTel span 中。切到 Anthropic 只需改一行；系统消息被提升到顶层 `system` 字段，非 2xx 表现为 `LlmHttpException`。见 **[[LLM 连接器|zh-LLM-Connectors]]**。

---

## 8 — ReAct 智能体

ReAct（推理 + 行动）循环在 LLM 推理与工具执行间交替。`ReActAgent<S>` 为你构建整个 `Graph<S>`：`llm` 节点、`tools` 节点、`done` 终止。

```java
Graph<AgentState> agentGraph = ReActAgent.<AgentState>builder()
    .client(client)
    .tool(calcDef, calculator)
    .requestFactory(state -> LlmRequest.builder().messages(state.history()).model("gpt-4o-mini").build())
    .responseFolder((state, response) -> state.withHistory(append(state.history(), ChatMessage.assistant(response.content()))))
    .toolResultFolder((state, results) -> state.withLastToolResults(results).withHistory(append(state.history(), toMessages(results))))
    .build()
    .buildGraph();

ExecutionResult<AgentState> result = agentGraph.run(AgentState.of("What is 42 * 17?"));
```

智能体循环直到 LLM 不再请求工具。因结果是普通 `Graph<S>`，可用 `.subgraph("agent", agentGraph)` 嵌入更大图。组合**多个**智能体见 **[[多智能体模式|zh-Multi-Agent-Patterns]]**。

---

## 9 — RAG 流水线

在 TraceGraph 中每个 RAG 步骤都是节点——因此检索与生成都获得重试、追踪步骤与检查点。三节点：`retrieve`（向量搜索）→ `augment`（把上下文贴入系统提示词）→ `generate`（`ChatNode` 调 LLM）。在 retrieve 与 augment 间插入 rerank 节点提升相关性。换向量库只需改 `retrieveNode`。见 **[[RAG 检索增强|zh-RAG]]**。

---

## 10 — 重放与差异

重放从任意步骤重执行已保存追踪——用于调试、提示词迭代与回归测试。接入记录器与存储：

```java
InMemoryTraceStore<RagState> traceStore = new InMemoryTraceStore<>();
Graph<RagState> graph = Graph.<RagState>builder()
    .traceRecorder(new RecordingTraceRecorder<>(traceStore)).build();

ExecutionTrace<RagState> trace = traceStore.load(result.executionId()).orElseThrow();

ReplayRunner<RagState> runner = ReplayRunner.of(trace, improvedGraph);
ExecutionResult<RagState> forked = runner.reRunFrom(1);   // 携带 forkedFrom* 血缘
```

`stepIndex == -1` 用原种子从入口重放；第二个参数可覆盖种子。`TraceDiff.between(original, forked)` 比较两条追踪（`divergenceIndex()`、`sameFinalState()`、`identical()`）。持久追踪用 `JsonFileTraceStore.of(dir, RagState.class)`。见 **[[可观测性与重放|zh-Observability-and-Replay]]**。

---

## 11 — HITL 中断

人在回路暂停让操作者在继续前检视或批准状态——一等中断机制，无轮询。

```java
Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("draft", draftNode).node("review", reviewNode).node("publish", publishNode)
    .edge("draft", "review").edge("review", "publish")
    .entry("draft").terminal("publish")
    .checkpointStore(checkpointStore)
    .interruptBefore("publish")
    .build();
```

`interruptBefore(name)` 在节点前暂停并写检查点；`interruptAfter(name)` 运行节点、写检查点、再暂停。运行返回 `Status.INTERRUPTED`（不抛异常）。检视后 `graph.resume(id)` 继续。恢复前修改状态：加载、修改、重存检查点，再 `resume`。不支持 `parallel(...)` 内逐分支中断。starter 暴露 `POST /tracegraph/traces/{id}/resume`——见 **[[REST API 参考|zh-REST-API-Reference]]**。

---

**下一步：** **[[实用手册|zh-Cookbook]]** 看任务导向食谱，或 **[[架构设计|zh-Architecture]]** 看设计理由。
