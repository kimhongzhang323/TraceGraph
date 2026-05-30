# Multi-Agent Patterns

`ReActAgent<S>` is the single-agent primitive (see **[[LLM Connectors]]**). The `0.3.0` release adds four ways to compose multiple ReAct agents into a `Graph<S>`, plus a termination listener that gives all of them convergence guarantees. These live in `tracegraph-connectors`.

| Pattern | Type | Shape |
|---|---|---|
| Peer-to-peer delegation | `HandoffNode<S>` | one agent hands control to another by name |
| Role/tool isolation | `AgentProfile<S>` | per-agent system prompt + tool set |
| N-agent rotation | `GroupChatAgent<S>` | round-robin or LLM-selected speakers |
| Parallel consensus | `VotingNode<S>` | fan out candidates, aggregate with a `Tally` |

## HandoffNode — peer-to-peer delegation

One agent hands control directly to another by reading a target name from state — **no central supervisor in the loop**.

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

Selector semantics:

- `"continue"` (reserved) — falls through to the next declared target.
- `null` or an **unknown** name — terminates at `"done"`.

## AgentProfile — role and tool isolation

`AgentProfile<S>` is a `(name, systemPrompt, List<Tool>, List<ToolDefinition>, memoryScope)` record that overrides the role prompt and tools on a `ReActAgent.Builder`. Calling `.profile(...)` **replaces** any prior `tool(...)` registrations, so two agents on the same `LlmClient` cannot see each other's tools.

```java
AgentProfile<S> researcher = new AgentProfile<>(
        "researcher",
        "You are a research analyst.",
        List.of(searchTool, fetchTool),
        List.of(searchDef, fetchDef),
        Function.identity());   // memoryScope mapper

Graph<S> graph = ReActAgent.<S>builder()
        .client(llmClient)
        .profile(researcher)
        .requestFactory(...)
        .responseFolder(...)
        .toolResultFolder(...)
        .build()
        .buildGraph();
```

The `memoryScope` ties into the `MemoryStore` (see **[[Memory]]**) so each agent reads/writes its own scope.

## GroupChatAgent — round-robin or LLM-selected speakers

Rotates N named `ReActAgent`s using a `SpeakerSelector<S>` strategy and halts on a user-supplied `terminationPredicate`.

```java
Graph<S> chat = GroupChatAgent.<S>builder()
        .agent("alice", aliceGraph)
        .agent("bob", bobGraph)
        .agent("carol", carolGraph)
        .speakerSelector(SpeakerSelector.roundRobin())   // or SpeakerSelector.llm(...)
        .terminationPredicate(state -> state.rounds() >= 4)
        .build()
        .buildGraph();
```

- `SpeakerSelector.roundRobin()` — cycles speakers in declaration order.
- `SpeakerSelector.llm(...)` — lets an LLM pick the next speaker.

## VotingNode — parallel consensus

Fans out across candidate ReAct subgraphs using `parallel(...)` (see **[[Runtime Features]]**), then aggregates their states through a `Tally`.

```java
Node<S> vote = VotingNode.<S>builder()
        .candidate("alice", aliceGraph)
        .candidate("bob", bobGraph)
        .candidate("carol", carolGraph)
        .tally(Tally.majority(State::answer))   // or Tally.firstNonNull(State::answer)
        .build();
```

Built-in tallies:

- `Tally.majority(Function<S, String>)` — most common answer wins.
- `Tally.firstNonNull(Function<S, String>)` — first candidate with a non-null answer.

## Termination guarantees

Layer in `TerminationListener<S>` from `tracegraph-observability` for convergence guarantees that work across **all four** patterns:

```java
TerminationListener<S> term = TerminationListener.<S>builder()
        .maxTurns(8)
        .afterNode("done")
        .stateMatches(state -> state.isConverged())
        .build();
```

When a predicate fires, the executor surfaces a clean **`Status.TERMINATED`** outcome (the listener throws `TerminationSignalException`, which the executor catches).

## Agent-to-agent (A2A) module

For agents that need to **message each other** across process or network boundaries — rather than compose inside one `Graph<S>` — see the `tracegraph-a2a` module:

- `Agent<S>`, `AgentBus` SPI, `InMemoryAgentBus` (virtual-thread dispatch).
- `AgentMessage` record with `of()` / `reply()` factories; `AgentTimeoutException`.
- HTTP transport (`A2AHttpClient`, `A2AMessage`, `A2AHttpException`) for wire-compatible Google A2A JSON exchange.
- Spring Boot: `A2AAutoConfiguration` registers `InMemoryAgentBus`; `A2AController` exposes `POST /a2a/messages`.

```java
A2AMessage request = A2AMessage.builder()
    .from("manager_agent")
    .to("worker_agent")
    .payload("{\"task\": \"summarize_logs\"}")
    .build();

A2AMessage response = dispatcher.sendAndWait(request);
```

---

**Related:** **[[LLM Connectors]]** · **[[Memory]]** · **[[Observability and Replay]]** · **[[Evaluation]]**
