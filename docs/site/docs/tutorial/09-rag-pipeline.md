# 09 — RAG Pipeline

Retrieval-Augmented Generation (RAG) combines a vector search step with an LLM generation step. In TraceGraph, each step is a node — giving you full observability, retries, and checkpointing over the entire retrieval-generation loop.

## State record

```java
record RagState(
    String query,
    List<String> retrievedChunks,
    String systemPrompt,
    String answer
) {
    static RagState of(String query) {
        return new RagState(query, List.of(), null, null);
    }
}
```

## Retrieve node

The retrieve node calls a vector database and populates `retrievedChunks`. This example uses a hypothetical `VectorStore` abstraction — swap in any provider.

```java
Node<RagState> retrieveNode = (state, ctx) -> {
    List<String> chunks = vectorStore.similaritySearch(state.query(), 5);
    return new RagState(state.query(), chunks, null, null);
};
```

## Augment node

Assembles the system prompt by injecting retrieved context:

```java
Node<RagState> augmentNode = (state, ctx) -> {
    String context = String.join("\n---\n", state.retrievedChunks());
    String systemPrompt = """
        Answer the question using only the context below.
        Context:
        %s
        """.formatted(context);
    return new RagState(state.query(), state.retrievedChunks(), systemPrompt, null);
};
```

## Generate node

Calls the LLM with the augmented prompt:

```java
LlmClient llm = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini")
    .build();

Node<RagState> generateNode = ChatNode.<RagState>builder()
    .client(llm)
    .requestBuilder(state -> LlmRequest.builder()
        .systemMessage(state.systemPrompt())
        .message(ChatMessage.user(state.query()))
        .model("gpt-4o-mini")
        .maxTokens(1024)
        .build())
    .responseFolder((state, response) ->
        new RagState(state.query(), state.retrievedChunks(), state.systemPrompt(), response.content()))
    .build();
```

## Assemble the graph

```java
Graph<RagState> ragGraph = Graph.<RagState>builder()
    .node("retrieve", retrieveNode, RetryPolicy.of(3, BackoffStrategy.exponential(200, 5_000)))
    .node("augment",  augmentNode)
    .node("generate", generateNode, RetryPolicy.of(2, BackoffStrategy.fixed(1_000)))
    .edge("retrieve", "augment")
    .edge("augment",  "generate")
    .entry("retrieve")
    .terminal("generate")
    .build();
```

## Adding a reranker

Insert a rerank node between retrieval and augmentation to improve chunk relevance:

```java
Node<RagState> rerankNode = (state, ctx) -> {
    List<String> reranked = reranker.rerank(state.query(), state.retrievedChunks(), 3);
    return state.withRetrievedChunks(reranked);
};

// Add .node("rerank", rerankNode) and .edge("retrieve", "rerank"), .edge("rerank", "augment")
```

## Key takeaways

- Each RAG step (retrieve, augment, generate) is a separate node — each gets its own retry policy and trace step.
- `ChatNode` handles the LLM call with automatic token reporting.
- The graph is observable end-to-end: every step's before/after state appears in `ExecutionTrace`.
- Swap in any vector store by changing the `retrieveNode` implementation — the rest of the graph is unchanged.
