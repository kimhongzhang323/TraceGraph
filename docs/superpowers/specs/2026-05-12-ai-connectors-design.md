# AI Connectors Complete — Design Spec

**Date:** 2026-05-12  
**Branch:** feat/ai-connectors-complete  
**Scope:** tracegraph-connectors

---

## Goal

Fill three gaps in the current connector layer:

1. All major LLM providers covered with up-to-date default models
2. Embedding SPI + implementations (OpenAI, Gemini, Ollama)
3. VectorStore SPI + implementations (Qdrant, Weaviate, Pinecone, pgvector)
4. StructuredOutput retry-with-error-feedback loop (hallucination mitigation)
5. Spring Boot auto-config wired for embedding and vector store providers

---

## Architecture

Everything lives in `tracegraph-connectors`. No new modules. Three new sub-packages added alongside the existing `llm/` package.

```
tracegraph-connectors/src/main/java/io/tracegraph/connectors/
├── llm/
│   ├── LlmClient.java              (existing)
│   ├── OpenAiLlmClient.java        (update default model → gpt-4.1)
│   ├── AnthropicLlmClient.java     (update default model → claude-sonnet-4-6)
│   ├── GeminiLlmClient.java        (update default → gemini-3-flash-preview, add tool calling)
│   ├── DeepSeekLlmClient.java      (NEW — delegates to OpenAI-compatible adapter)
│   └── OllamaLlmClient.java        (existing)
├── embedding/
│   ├── EmbeddingClient.java        (NEW SPI)
│   ├── OpenAiEmbeddingClient.java  (NEW — text-embedding-3-large)
│   ├── GeminiEmbeddingClient.java  (NEW — gemini-embedding-004)
│   └── OllamaEmbeddingClient.java  (NEW — nomic-embed-text)
├── vectorstore/
│   ├── VectorStore.java            (NEW SPI)
│   ├── Document.java               (NEW record)
│   ├── SearchResult.java           (NEW record)
│   ├── QdrantVectorStore.java      (NEW — REST/JSON, no extra dep)
│   ├── WeaviateVectorStore.java    (NEW — REST/JSON)
│   ├── PineconeVectorStore.java    (NEW — REST/JSON)
│   └── PgVectorStore.java          (NEW — JDBC, optional dep)
└── structured/
    └── StructuredOutput.java       (existing + extractWithRetry)
```

---

## SPI Contracts

### EmbeddingClient

```java
@FunctionalInterface
public interface EmbeddingClient {
    List<float[]> embed(List<String> texts);

    default float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }
}
```

- Batch-first: callers batch where possible, implementations may also batch internally.
- Implementations must be thread-safe.

### VectorStore

```java
public interface VectorStore {
    void upsert(List<Document> documents);
    List<SearchResult> search(float[] vector, int topK);
    List<SearchResult> search(float[] vector, int topK, Map<String, Object> filter);
    void delete(List<String> ids);

    default void upsert(Document document) { upsert(List.of(document)); }
}
```

- `upsert` is idempotent (insert-or-replace by id).
- `filter` semantics are provider-specific; implementations document their supported keys.
- Implementations must be thread-safe.

### Document / SearchResult

```java
public record Document(
    String id,
    String text,
    float[] vector,
    Map<String, Object> metadata
) {}

public record SearchResult(Document document, float score) {}
```

`metadata` is always non-null (use `Map.of()` for empty). `score` is cosine similarity in [0,1] normalized by each provider where possible.

---

## LLM Provider Updates

| Client | Default model | Notes |
|---|---|---|
| AnthropicLlmClient | `claude-sonnet-4-6` | was `claude-3-5-sonnet-20241022` |
| GeminiLlmClient | `gemini-3-flash-preview` | was `gemini-2.5-flash`; also adds tool calling |
| OpenAiLlmClient | `gpt-4.1` | no previous default; now set |
| DeepSeekLlmClient | `deepseek-chat` | NEW; OpenAI-compatible, delegates serialization |

### DeepSeekLlmClient

DeepSeek's chat API is a strict superset of the OpenAI chat-completions format. `DeepSeekLlmClient` holds an `OpenAiLlmClient` internally configured to `https://api.deepseek.com/v1/chat/completions` with the DeepSeek API key. The public API mirrors `OpenAiLlmClient.Builder` with a `reasoner()` convenience method that sets model to `deepseek-reasoner`.

### GeminiLlmClient Tool Calling

Gemini's function-calling format differs from OpenAI:
- Tools go under `tools[].functionDeclarations[]` (not `tools[].function`)
- Tool call response is in `candidates[0].content.parts[].functionCall`
- Tool result goes back as a `functionResponse` part in a `user` turn

Add tool serialization/deserialization to the existing `toRequestBody` / `parseResponse` methods. The `UnsupportedOperationException` guard is removed.

---

## Embedding Implementations

All three use JDK `HttpClient` + Jackson (already an optional dep). No new dependencies.

| Client | Endpoint | Dimension |
|---|---|---|
| OpenAiEmbeddingClient | `https://api.openai.com/v1/embeddings` | 3072 (text-embedding-3-large) |
| GeminiEmbeddingClient | `https://generativelanguage.googleapis.com/v1beta/models/{model}:embedContent` | 768 |
| OllamaEmbeddingClient | `http://localhost:11434/api/embed` | model-dependent |

Builder pattern mirrors existing LLM clients: `apiKey`, `model`, `httpClient`, `requestTimeout`.

---

## VectorStore Implementations

All four use JDK `HttpClient` + Jackson (Qdrant, Weaviate, Pinecone) or JDBC (pgvector). No new runtime dependencies beyond what connectors already declares as optional.

### Qdrant
- REST API: `PUT /collections/{collection}/points` (upsert), `POST /collections/{collection}/points/search`
- Filter: Qdrant's native `must`/`should` condition map passed through as-is

### Weaviate
- REST API: `POST /v1/objects` (upsert), `POST /v1/graphql` (search via GraphQL nearVector)
- Filter: Weaviate `where` operator map

### Pinecone
- REST API: `POST /vectors/upsert`, `POST /query`
- Region + index name required in builder

### PgVectorStore
- Uses existing JDBC pattern (same style as `JdbcMemoryStore`)
- Table: `tracegraph_vectors (id TEXT PK, text TEXT, vector VECTOR(n), metadata JSONB)`
- `initSchema()` is idempotent; dimension set in builder
- `pgvector` extension must be pre-installed in Postgres

---

## Hallucination Mitigation: StructuredOutput Retry

```java
public T extractWithRetry(LlmClient client, LlmRequest originalRequest, int maxAttempts) {
    // attempt 1: call LLM, try to parse
    // on failure: append assistant reply + user message with the parse error
    //             re-call LLM, try again
    // after maxAttempts: throw StructuredOutputException
}
```

Error feedback message injected into conversation:
> "Your previous response could not be parsed: {errorMessage}. Please respond with valid JSON matching the expected schema."

This closes the retry loop without external state; the corrected prompt is constructed inline.

---

## Spring Boot Auto-Config

Three new properties groups, all `@ConditionalOnMissingBean` so user beans always win:

```properties
# Embedding
tracegraph.embedding.provider=openai          # openai | gemini | ollama
tracegraph.embedding.api-key=...
tracegraph.embedding.model=...                # optional override
tracegraph.embedding.base-url=...            # optional override

# Vector Store
tracegraph.vectorstore.provider=qdrant        # qdrant | weaviate | pinecone | pgvector
tracegraph.vectorstore.url=...
tracegraph.vectorstore.api-key=...
tracegraph.vectorstore.collection=...
tracegraph.vectorstore.dimension=1536         # required for pgvector

# DeepSeek (extends existing tracegraph.llm.provider)
tracegraph.llm.provider=deepseek
tracegraph.llm.api-key=...
```

New auto-config class: `EmbeddingAutoConfiguration` and `VectorStoreAutoConfiguration`, both in `boot/` package of the starter. Both are optional — guarded by `@ConditionalOnClass`.

---

## Testing Strategy

| Component | Test approach |
|---|---|
| EmbeddingClient impls | `HttpServer` (JDK built-in) mock, no Wiremock dep needed |
| VectorStore impls (Qdrant/Weaviate/Pinecone) | Same JDK HttpServer mock |
| PgVectorStore | H2 in-memory (schema only, no actual vector ops) |
| GeminiLlmClient tool calling | JDK HttpServer mock |
| DeepSeekLlmClient | Delegates to OpenAiLlmClient; test the delegation wiring only |
| StructuredOutput.extractWithRetry | MockLlmClient sequence: fail × N then succeed |
| Auto-config | Spring Boot test slice with mocked beans |

---

## Out of Scope

- Native streaming for embedding clients
- Hybrid search (vector + BM25) in WeaviateVectorStore
- Async `VectorStore` variants
- Reranking
- Per-branch fork inside `parallel(...)` for RAG
