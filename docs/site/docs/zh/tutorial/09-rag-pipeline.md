---
title: RAG 管道
---

# RAG 管道


# RAG 管道



RAG（Retrieval-Augmented Generation）是一种常见模式：先检索相关文档，再把检索到的上下文与问题一起发给 LLM，最后合成答案。在 TraceGraph 中，典型步骤是：

1. `retrieve` 节点从向量库或文档索引中检索候选文档。
2. `llm` 节点将检索到的片段与问题一并发送给模型。
3. `merge` 节点根据 LLM 返回的结果更新状态并决定是否需要迭代检索。

示例配置：使用 `tracegraph-connectors` 提供的向量 DB 适配器与 `ChatNode`。

练习：在 `examples/rag-agent` 基础上把检索阈值设为更严格，并观察回答召回率的变化。
