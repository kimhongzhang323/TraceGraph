---
title: RAG 管道
---

# 09 — RAG 管道

检索增强生成（Retrieval-Augmented Generation，RAG）将向量搜索步骤与 LLM 生成步骤相结合。在 TraceGraph 中，每个步骤都是一个节点——为整个检索生成循环提供完整的可观测性、重试机制和检查点支持。

## 状态 Record

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

## 检索节点

检索节点调用向量数据库并填充 `retrievedChunks`。以下示例使用假设的 `VectorStore` 抽象——可替换为任意提供商。

```java
Node<RagState> retrieveNode = (state, ctx) -> {
    List<String> chunks = vectorStore.similaritySearch(state.query(), 5);
    return new RagState(state.query(), chunks, null, null);
};
```

## 增强节点

通过注入检索到的上下文来组装系统提示：

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

## 生成节点

使用增强后的提示调用 LLM：

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

## 组装图

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

## 添加重排序器

在检索和增强之间插入重排序节点，以提高文本块的相关性：

```java
Node<RagState> rerankNode = (state, ctx) -> {
    List<String> reranked = reranker.rerank(state.query(), state.retrievedChunks(), 3);
    return state.withRetrievedChunks(reranked);
};

// 添加 .node("rerank", rerankNode)
// 并修改边：.edge("retrieve", "rerank"), .edge("rerank", "augment")
```

## 要点总结

- RAG 的每个步骤（检索、增强、生成）都是独立的节点——每个步骤都有自己的重试策略和追踪步骤。
- `ChatNode` 负责处理 LLM 调用，并自动上报令牌使用量。
- 图具备端到端可观测性：每个步骤的前后状态均显示在 `ExecutionTrace` 中。
- 只需更改 `retrieveNode` 实现即可替换向量存储——图的其余部分保持不变。
