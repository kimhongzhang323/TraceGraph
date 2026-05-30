# FAQ

## Is TraceGraph a LangGraph clone?

No. It borrows the graph-runtime idea space, but it is intentionally designed around **Java typing, explicit runtime control, and JVM integration points**. The thesis is a production-grade agent runtime for the JVM — typed graphs, durable memory, deep observability — not a line-by-line port.

## Can I use it without LLMs?

Yes. The core runtime has **no dependency on model providers**. Use it for any typed workflow or orchestration graph. LLM support is additive via `tracegraph-connectors`.

## Can I use it with Spring Boot only?

Yes, but the starter is **additive**. Under the hood you are still working with the same `Graph<S>` runtime and SPI abstractions. See **[[Spring Boot Integration]]**.

## Does it support durable resume today?

Yes — through the `CheckpointStore` SPI and `tracegraph-runtime` (`InMemoryCheckpointStore`, `JdbcCheckpointStore`). The durability story is still evolving and should be treated as pre-1.0 infrastructure. See **[[Runtime Features]]**.

## How do I swap LLM providers without changing graph logic?

Replace **one line** — the `LlmClient` construction. `ChatNode`, `ReActAgent`, tool definitions, and your state type are unchanged because `LlmClient` is the only dependency:

```java
// OpenAI
LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY")).model("gpt-4o").build();

// Anthropic — everything else stays identical
LlmClient client = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY")).model("claude-3-5-sonnet-20241022").build();
```

## What happens when an LLM provider returns a non-2xx status?

Both HTTP adapters throw **`LlmHttpException`** (`statusCode()` + `body()`). It propagates through `ChatNode.apply()` and is treated as a node failure. If a `RetryPolicy` is configured on the node, the executor retries the whole call — a 429 is the canonical retry-with-backoff case. See **[[LLM Connectors]]**.

## Can I use a local model (Ollama, LM Studio)?

Yes. Any server implementing the OpenAI Chat Completions spec works via `OpenAiLlmClient` with a custom `endpoint`:

```java
OpenAiLlmClient.builder()
    .apiKey("local")
    .endpoint("http://localhost:11434/v1")
    .model("llama3.1:8b")
    .build();
```

## Does streaming work with `ChatNode` / `ReActAgent`?

`ChatNode` uses `complete()`, not `stream()`. There are **two distinct kinds of streaming**:

- **LLM token streaming** — `LlmClient.stream(request)` returns incremental token deltas for a UI.
- **Graph-level event streaming** — `Graph.stream(initial)` (and the SSE endpoint) emit node enter/exit/retry/complete events.

See **[[LLM Connectors]]** and **[[Runtime Features]]**.

## Why is `Node<S>` single-parameter and not `Node<S, R>`?

By design. Use **state composition** for sub-results — fold them into the state object rather than threading a second type parameter. Two type parameters double the inference burden on the fluent builder. This is a hard rule in the project.

## Are nodes guaranteed to run exactly once?

No — nodes are **at-least-once on resume**. If a crash happens mid-node, that node re-runs from attempt 1 on resume. Use `ctx.idempotencyKey()` inside side-effecting nodes for your own deduplication, and keep **edge predicates pure**. See **[[Execution Model]]**.

## Where do checkpoints get written relative to edges?

**After node exit, before edge resolution.** Resume re-evaluates the outgoing edges of the saved `lastCompletedNode` and continues. See **[[Runtime Features]]**.

## Do retries create extra trace steps or spans?

No. Retries are **span events on the same span** (no span-per-attempt) and produce **no extra trace steps** — `TraceStep.attempts` records the count. See **[[Observability and Replay]]**.

## Do parallel branches show up in traces/listeners?

No. Branches inside `parallel(...)` are anonymous — **no names, no path entries, no listener events, no spans** — and produce **one** trace step. This is the Phase 2c contract. See **[[Runtime Features]]**.

## What's the difference between working memory and the MemoryStore?

The **state object** is working memory (within a single execution). The **`MemoryStore`** is for cross-execution data (sessions, long-term facts). Vector/semantic search lives in `tracegraph-rag`, not the memory SPI. See **[[Memory]]** and **[[RAG]]**.

## Which JDK do I need?

**JDK 21.** Records, pattern matching, and virtual threads are used throughout. If `mvn -version` reports Java 17 or lower, update `JAVA_HOME` before building.

## Is the API stable?

Not yet — it is **pre-1.0** and may change between minor releases (every break is documented in the changelog). `tracegraph-core` is the safest module to build against first. Release artifacts are the real compatibility boundary; connector modules are integration helpers, not a provider-abstraction promise.

## How do I contribute?

1. Use JDK 21 and Maven 3.9+.
2. Run `mvn -B -ntp verify` before opening a PR.
3. Keep changes scoped; add or update tests when behaviour changes.
4. Prefer small, reviewable PRs over broad refactors.

---

**Still stuck?** Open an issue on the [repository](https://github.com/kimhongzhang323/TraceGraph/issues).
