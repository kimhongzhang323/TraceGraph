# Tutorial

A guided, build-up walkthrough of TraceGraph. Each part adds one capability to a shared example. Read top-to-bottom the first time; use it as a reference afterwards.

> 🌐 中文版： **[[教程|zh-Tutorial]]**

**Contents**

1. [Nodes & Edges](#1--nodes--edges)
2. [State & Context](#2--state--context)
3. [Retries & Failures](#3--retries--failures)
4. [Checkpoints & Resume](#4--checkpoints--resume)
5. [Parallel & Send](#5--parallel--send)
6. [Memory](#6--memory)
7. [LLM & Tools](#7--llm--tools)
8. [ReAct Agent](#8--react-agent)
9. [RAG Pipeline](#9--rag-pipeline)
10. [Replay & Diff](#10--replay--diff)
11. [HITL Interrupts](#11--hitl-interrupts)

---

## 1 — Nodes & Edges

Nodes and edges are the two primitives every TraceGraph program is built from.

A shared state record evolves as the tutorial adds features:

```java
record PipelineState(String input, String cleaned, String result) {
    static PipelineState of(String input) {
        return new PipelineState(input, null, null);
    }
}
```

A `Node<S>` is a `@FunctionalInterface` with signature `(S state, Context ctx) -> S`. It receives the current state, does its work, and returns the **next** state — it must never mutate the state it receives.

```java
Node<PipelineState> clean = (state, ctx) ->
    new PipelineState(state.input(), state.input().strip().toLowerCase(), null);

Node<PipelineState> shout = (state, ctx) ->
    new PipelineState(state.input(), state.cleaned(), state.cleaned().toUpperCase() + "!");
```

**Unconditional edges** (`.edge(from, to)`) always fire after the source node completes. **Conditional edges** (`.edge(from, to, predicate)`) are evaluated in declaration order; the first whose predicate returns `true` wins.

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("clean", clean)
    .node("shout", shout)
    .node("warn", (s, ctx) -> new PipelineState(s.input(), s.cleaned(), "[WARNING] empty input"))
    .edge("clean", "warn",  s -> s.cleaned().isEmpty())
    .edge("clean", "shout", s -> !s.cleaned().isEmpty())
    .entry("clean")
    .terminal("shout")
    .terminal("warn")
    .build();

ExecutionResult<PipelineState> result = graph.run(PipelineState.of("  Hello  "));
System.out.println(result.finalState().result()); // HELLO!
```

**Takeaways:** `Node<S>` is `(S, Context) -> S`; return the new state, never mutate. Conditional edges are first-match-wins in declaration order. **Edge predicates must be pure** — no side effects, no I/O (they are re-evaluated on resume).

---

## 2 — State & Context

Every node receives the state and a `Context`. Real pipelines accumulate data, so grow the record:

```java
record PipelineState(String input, String cleaned, String result, List<String> log) {
    static PipelineState of(String input) {
        return new PipelineState(input, null, null, List.of());
    }
    PipelineState withLog(String entry) {
        var next = new ArrayList<>(log);
        next.add(entry);
        return new PipelineState(input, cleaned, result, List.copyOf(next));
    }
}
```

`Context` carries execution-scoped metadata:

| Method | Use |
|---|---|
| `ctx.executionId()` | stable UUID per run — log/correlate across services |
| `ctx.idempotencyKey()` | attempt-scoped key — pass to HTTP/JDBC so retries don't double-apply |
| `ctx.memory()` | the `MemoryStore` (see Part 6) |
| `ctx.reportUsage(prompt, completion)` | LLM nodes report token usage (see Part 7) |

```java
Node<PipelineState> clean = (state, ctx) -> {
    String cleaned = state.input().strip().toLowerCase();
    return new PipelineState(state.input(), cleaned, null, state.log())
        .withLog("[%s] cleaned".formatted(ctx.executionId()));
};
```

**State composition over generic results.** TraceGraph uses a single type parameter `<S>`. Instead of `Node<S, R>`, fold sub-results into fields on the state record — two type parameters break builder inference and complicate resumption.

---

## 3 — Retries & Failures

Retry is **graph definition**, not runtime config — so the policy is reproducible, versionable, and visible in traces.

```java
RetryPolicy fixed       = RetryPolicy.of(3, BackoffStrategy.fixed(200));
RetryPolicy exponential = RetryPolicy.of(5, BackoffStrategy.exponential(100, 10_000));
```

`BackoffStrategy.fixed(ms)` waits a constant delay; `BackoffStrategy.exponential(baseMs, maxMs)` doubles each attempt, capped at `maxMs`. Attach per-node (third argument) or set a graph default — **per-node beats default**.

```java
Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("callApi", callApiNode, RetryPolicy.of(5, BackoffStrategy.exponential(100, 8_000)))
    .node("process", processNode)
    .defaultRetryPolicy(RetryPolicy.of(2, BackoffStrategy.fixed(500)))
    .entry("callApi").terminal("process")
    .build();
```

Use `ctx.idempotencyKey()` to make external calls safe across attempts:

```java
Node<PipelineState> callApiNode = (state, ctx) -> {
    String response = httpClient.post("/enrich", state.cleaned(),
        Map.of("Idempotency-Key", ctx.idempotencyKey()));
    return state.withResult(response);
};
```

`Error` and `InterruptedException` **always short-circuit** retries. A node that exhausts attempts surfaces a `NodeExecutionException` and sets `ExecutionResult.status` to `FAILED`:

```java
if (result.status() == Status.FAILED) {
    result.failureCause().ifPresent(Throwable::printStackTrace);
}
```

---

## 4 — Checkpoints & Resume

Long-running graphs need to survive restarts. After a node exits successfully and **before** the outgoing edge is evaluated, TraceGraph writes a checkpoint (`executionId`, `lastCompletedNode`, state). On resume it loads the checkpoint, re-evaluates the outgoing edges of `lastCompletedNode`, and continues.

```java
CheckpointStore<PipelineState> store = new InMemoryCheckpointStore<>();   // dev

Graph<PipelineState> graph = Graph.<PipelineState>builder()
    .node("fetch",   fetchNode, RetryPolicy.of(3, BackoffStrategy.exponential(200, 5_000)))
    .node("enrich",  enrichNode)
    .node("persist", persistNode)
    .edge("fetch", "enrich").edge("enrich", "persist")
    .entry("fetch").terminal("persist")
    .checkpointStore(store)
    .build();
```

For production, `JdbcCheckpointStore` stores checkpoints in one table — call `initSchema()` once:

```java
JdbcCheckpointStore<PipelineState> store = new JdbcCheckpointStore<>(dataSource, PipelineState.class);
store.initSchema();
```

Run with an explicit id, then resume:

```java
String executionId = UUID.randomUUID().toString();
graph.run(PipelineState.of("hello"), executionId);   // crashes mid-way
ExecutionResult<PipelineState> resumed = graph.resume(executionId);  // COMPLETED
```

**At-least-once:** if a crash happens mid-node (after it starts, before the checkpoint is written), that node re-runs from attempt 1 on resume. Design nodes to be idempotent with `ctx.idempotencyKey()`. **Edge predicates must be pure** — they are re-evaluated on resume.

---

## 5 — Parallel & Send

Two forms of concurrency: static `parallel` branches at build time, and dynamic `sendAll` fan-out at runtime.

```java
Graph<EnrichState> graph = Graph.<EnrichState>builder()
    .parallel("enrich",
        List.of(geoNode, sentimentNode),
        (a, b) -> new EnrichState(a.input(), a.geoResult(), b.sentimentResult(), null))
    .node("combine", (s, ctx) -> s.withCombined(s.geoResult() + " | " + s.sentimentResult()))
    .edge("enrich", "combine")
    .entry("enrich").terminal("combine")
    .build();
```

All branches receive the **same input state**; results merge in declaration order. Branches run on a virtual-thread executor, are **anonymous** (no trace steps, no listener events), and **first-by-declaration-order failure wins**. A user-supplied `.executor(...)` is **not** shut down by the graph.

When targets are runtime-determined, use `NodeResult.sendAll(...)` inside a `RoutingNode`:

```java
.routingNode("dispatch", (state, ctx) -> {
    List<Send<BatchState>> sends = state.items().stream()
        .map(item -> new Send<>("process", state.withSingleItem(item)))
        .toList();
    return NodeResult.sendAll(sends, BatchState::merge, state);
})
```

`sendAll` expands identically to `parallel` at runtime.

---

## 6 — Memory

Working memory is the state object (one execution). The `MemoryStore` SPI provides **cross-execution** persistence, reachable from any node via `ctx.memory()`:

```java
ctx.memory().put("user:42", "preferences", Map.of("lang", "en"));
Object prefs   = ctx.memory().get("user:42", "preferences");
Set<String> ks = ctx.memory().keys("user:42");
ctx.memory().delete("user:42", "preferences");
```

The first argument is the **scope** (user/session/category), the second the key. Wire it with `.memoryStore(store)`; if none is wired, `ctx.memory()` is a no-op that discards writes.

```java
Node<ChatState> rememberNode = (state, ctx) -> {
    var updated = new ArrayList<>(state.history());
    updated.add(state.lastTurn());
    ctx.memory().put("session:" + state.sessionId(), "history", List.copyOf(updated));
    return state;
};
```

Production implementations:

```java
MemoryStore fileStore = FileMemoryStore.of(Path.of("/var/tracegraph/memory"));
JdbcMemoryStore jdbcStore = new JdbcMemoryStore(dataSource);
jdbcStore.initSchema();
```

Both round-trip heterogeneous values (strings, numbers, lists, maps) via Jackson polymorphic serialization, and reject scope/key values containing `/`, `\`, or `..`. With `tracegraph-memory` + Jackson + a `DataSource`, `MemoryAutoConfiguration` registers a `JdbcMemoryStore` automatically. See **[[Memory]]**.

---

## 7 — LLM & Tools

The connectors module gives a vendor-neutral `LlmClient` and a `ChatNode<S>` adapter.

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
    default Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) { /* ... */ }
}
```

`ChatNode<S>` bridges `LlmClient` and `Node<S>` via two functions — `requestBuilder` (state → request) and `responseFolder` (state + response → state):

```java
LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY")).model("gpt-4o-mini").build();

Node<ChatState> chatNode = ChatNode.<ChatState>builder()
    .client(client)
    .requestBuilder(state -> LlmRequest.builder()
        .message(ChatMessage.user(state.userMessage()))
        .model("gpt-4o-mini").maxTokens(512).build())
    .responseFolder((state, response) -> new ChatState(
        state.userMessage(), response.content(),
        response.usage().promptTokens(), response.usage().completionTokens()))
    .build();
```

`ChatNode` automatically calls `ctx.reportUsage(...)` after each response, so token usage appears in trace steps and OTel spans. Swap to Anthropic by changing one line; system messages are lifted into the top-level `system` field, and non-2xx responses surface as `LlmHttpException(statusCode, body)`. See **[[LLM Connectors]]**.

---

## 8 — ReAct Agent

The ReAct (Reason + Act) loop alternates between LLM reasoning and tool execution. `ReActAgent<S>` builds the whole `Graph<S>` for you: an `llm` node, a `tools` node, and a `done` terminal.

```java
Graph<AgentState> agentGraph = ReActAgent.<AgentState>builder()
    .client(client)
    .tool(calcDef, calculator)
    .requestFactory(state -> LlmRequest.builder().messages(state.history()).model("gpt-4o-mini").build())
    .responseFolder((state, response) -> state.withHistory(
        append(state.history(), ChatMessage.assistant(response.content()))))
    .toolResultFolder((state, results) -> state
        .withLastToolResults(results)
        .withHistory(append(state.history(), toMessages(results))))
    .build()
    .buildGraph();

ExecutionResult<AgentState> result = agentGraph.run(AgentState.of("What is 42 * 17?"));
System.out.println(result.finalState().finalAnswer()); // 714.0
```

The agent loops until the LLM stops requesting tools. Because the result is a regular `Graph<S>`, embed it with `.subgraph("agent", agentGraph)` inside a larger graph. To compose **multiple** agents, see **[[Multi-Agent Patterns]]**.

---

## 9 — RAG Pipeline

In TraceGraph each RAG step is a node — so retrieval and generation both get retries, trace steps, and checkpointing.

```java
record RagState(String query, List<String> retrievedChunks, String systemPrompt, String answer) {
    static RagState of(String query) { return new RagState(query, List.of(), null, null); }
}

Node<RagState> retrieveNode = (state, ctx) ->
    new RagState(state.query(), vectorStore.similaritySearch(state.query(), 5), null, null);

Node<RagState> augmentNode = (state, ctx) -> {
    String context = String.join("\n---\n", state.retrievedChunks());
    String systemPrompt = "Answer using only the context below.\nContext:\n" + context;
    return new RagState(state.query(), state.retrievedChunks(), systemPrompt, null);
};

Node<RagState> generateNode = ChatNode.<RagState>builder()
    .client(llm)
    .requestBuilder(state -> LlmRequest.builder()
        .systemMessage(state.systemPrompt())
        .message(ChatMessage.user(state.query()))
        .model("gpt-4o-mini").maxTokens(1024).build())
    .responseFolder((state, response) ->
        new RagState(state.query(), state.retrievedChunks(), state.systemPrompt(), response.content()))
    .build();

Graph<RagState> ragGraph = Graph.<RagState>builder()
    .node("retrieve", retrieveNode, RetryPolicy.of(3, BackoffStrategy.exponential(200, 5_000)))
    .node("augment",  augmentNode)
    .node("generate", generateNode, RetryPolicy.of(2, BackoffStrategy.fixed(1_000)))
    .edge("retrieve", "augment").edge("augment", "generate")
    .entry("retrieve").terminal("generate")
    .build();
```

Insert a rerank node between retrieve and augment to improve relevance. Swap any vector store by changing `retrieveNode` — the rest is unchanged. See **[[RAG]]**.

---

## 10 — Replay & Diff

Replay re-executes a saved trace from any step — for debugging, prompt iteration, and regression testing. Wire a recorder and store:

```java
InMemoryTraceStore<RagState> traceStore = new InMemoryTraceStore<>();
Graph<RagState> graph = Graph.<RagState>builder()
    /* ... */
    .traceRecorder(new RecordingTraceRecorder<>(traceStore))
    .build();

ExecutionResult<RagState> result = graph.run(RagState.of("What is the capital of France?"));
ExecutionTrace<RagState> trace = traceStore.load(result.executionId()).orElseThrow();
```

Each `TraceStep` records `nodeName`, `before`, `after`, `attempts`, and per-step `usage`. Re-execute from a step against a (possibly modified) graph:

```java
ReplayRunner<RagState> runner = ReplayRunner.of(trace, improvedGraph);
ExecutionResult<RagState> forked = runner.reRunFrom(1);          // step index 1
// forked.executionId() != original; carries forkedFromExecutionId / forkedFromStepIndex
```

`stepIndex == -1` replays from entry with the original seed; a second argument overrides the seed. Compare two traces:

```java
TraceDiff<RagState> diff = TraceDiff.between(original, forked);
diff.divergenceIndex();   // first differing step
diff.sameFinalState();
diff.identical();
```

For durable traces use `JsonFileTraceStore.of(dir, RagState.class)` (atomic writes; lossy `Throwable` round-trip). See **[[Observability and Replay]]**.

---

## 11 — HITL Interrupts

Human-in-the-loop pauses let an operator inspect or approve state before continuing — a first-class interrupt mechanism, no polling.

```java
Graph<ApprovalState> graph = Graph.<ApprovalState>builder()
    .node("draft", draftNode).node("review", reviewNode).node("publish", publishNode)
    .edge("draft", "review").edge("review", "publish")
    .entry("draft").terminal("publish")
    .checkpointStore(checkpointStore)
    .interruptBefore("publish")   // pause before publishing
    .build();
```

`interruptBefore(name)` pauses just before the node and writes a checkpoint; `interruptAfter(name)` runs the node, writes the checkpoint, then pauses. The run returns `Status.INTERRUPTED` (it does not throw):

```java
ExecutionResult<ApprovalState> result = graph.run(ApprovalState.of("Draft content..."));
// result.status() == INTERRUPTED; save result.executionId()

ApprovalState pending = checkpointStore.load(result.executionId()).map(Checkpoint::state).orElseThrow();
// operator reviews pending.draft() ...

ExecutionResult<ApprovalState> done = graph.resume(result.executionId());  // COMPLETED
```

To modify state before resuming, load, modify, and re-save the checkpoint, then `resume`. Per-branch interrupts inside `parallel(...)` are not supported. The Spring Boot starter exposes `POST /tracegraph/traces/{id}/resume` — see **[[REST API Reference]]**.

---

**Next:** **[[Cookbook]]** for task-oriented recipes, or **[[Architecture]]** for the design rationale.
