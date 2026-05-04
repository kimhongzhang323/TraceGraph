# TraceGraph RAG Agent Example

Demonstrates a Retrieve-Augment-Generate pipeline using `langgraph-rag`. Uses `InMemoryVectorStore` and `MockEmbeddingClient` — no API keys or external services needed.

## Run

```bash
mvn -f examples/rag-agent/pom.xml exec:java
```

## What it demonstrates

- Setting up a `Retriever` with `InMemoryVectorStore` and `MockEmbeddingClient`
- Ingesting documents via `retriever.upsertText(scope, id, text, metadata)`
- A two-node graph: `retrieve` → `answer`
- `retrieve` node calls `retriever.retrieve(scope, query, topK)` and stores context in state
- `answer` node builds an LLM request with retrieved context and generates a response
