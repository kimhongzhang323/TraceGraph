# RAG Pipeline

Retrieval-Augmented Generation (RAG) is a powerful pattern where LLMs are enriched with external knowledge. TraceGraph provides specialized nodes and patterns to easily build RAG pipelines.

## Document Loading and Chunking

You can create a graph that begins with loading documents from various sources. These documents are then split into smaller chunks using a `ChunkingNode`.

## Embeddings and Vector Stores

TraceGraph integrates with various embedding models and vector databases. A typical RAG graph will:
1. Embed the incoming user query.
2. Search a vector store for similar chunks.
3. Inject the retrieved chunks into the context for the LLM.

## Building a RAG Graph

Using TraceGraph for RAG allows you to add complex routing, such as:
- Fallback queries if no results are found.
- Self-correction loops if the LLM determines the retrieved context is insufficient.
- Multi-step retrieval paths that refine the search based on intermediate answers.
